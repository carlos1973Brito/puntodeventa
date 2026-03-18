package com.pos.controller;

import com.pos.config.DatabaseConfig;
import com.pos.dto.FilaExcel;
import com.pos.dto.ImportPreview;
import com.pos.dto.ImportResult;
import com.pos.model.Usuario;
import com.pos.repository.impl.ProductoRepositoryImpl;
import com.pos.repository.impl.UsuarioRepositoryImpl;
import com.pos.repository.impl.VentaRepositoryImpl;
import com.pos.service.ExcelImportService;
import com.pos.service.impl.ExcelImportServiceImpl;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Controlador de la vista de Importación desde Excel.
 * Permite previsualizar y confirmar la importación de datos históricos.
 * Requisitos: 9.1, 9.5, 9.6, 9.7
 */
public class ImportarController implements MainController.UsuarioAwareController {

    private static final Logger log = LoggerFactory.getLogger(ImportarController.class);
    private static final DateTimeFormatter FMT_DT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    // --- Controles de selección de archivo ---
    @FXML private TextField txtRutaArchivo;
    @FXML private Button btnPrevisualizar;
    @FXML private Button btnConfirmar;

    // --- Resumen de previsualización ---
    @FXML private HBox hboxResumenPreview;
    @FXML private Label lblProductosNuevos;
    @FXML private Label lblVentasARegistrar;
    @FXML private Label lblFilasConError;

    // --- Tabla de previsualización ---
    @FXML private TableView<FilaExcel> tablaPreview;
    @FXML private TableColumn<FilaExcel, String> colEstado;
    @FXML private TableColumn<FilaExcel, String> colArticulo;
    @FXML private TableColumn<FilaExcel, String> colPrecio;
    @FXML private TableColumn<FilaExcel, String> colFechaHora;
    @FXML private TableColumn<FilaExcel, String> colError;

    // --- Errores de formato ---
    @FXML private Label lblTituloErrores;
    @FXML private TextArea txtErrores;

    // --- Resultado de importación ---
    @FXML private TextArea txtResultado;

    // --- Estado ---
    private ExcelImportService excelImportService;
    private File archivoSeleccionado;
    private ImportPreview ultimaPreview;
    private Usuario usuarioActual;

    @FXML
    public void initialize() {
        var sf = DatabaseConfig.buildSessionFactory();
        excelImportService = new ExcelImportServiceImpl(
                new ProductoRepositoryImpl(sf),
                new VentaRepositoryImpl(sf),
                new UsuarioRepositoryImpl(sf)
        );

        configurarColumnas();
    }

    @Override
    public void setUsuarioActual(Usuario usuario) {
        this.usuarioActual = usuario;
    }

    // -------------------------------------------------------------------------
    // Acciones
    // -------------------------------------------------------------------------

    @FXML
    private void onSeleccionarArchivo() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Seleccionar archivo Excel");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Archivos Excel", "*.xlsx", "*.xls"));

        File archivo = chooser.showOpenDialog(txtRutaArchivo.getScene().getWindow());
        if (archivo != null) {
            archivoSeleccionado = archivo;
            txtRutaArchivo.setText(archivo.getAbsolutePath());
            btnPrevisualizar.setDisable(false);
            // Limpiar estado previo
            limpiarEstado();
        }
    }

    @FXML
    private void onPrevisualizar() {
        if (archivoSeleccionado == null) return;

        try {
            ultimaPreview = excelImportService.previsualizarExcel(archivoSeleccionado);

            // Verificar si hubo error crítico de formato (sin filas y con errores)
            if (ultimaPreview.filas().isEmpty() && !ultimaPreview.errores().isEmpty()) {
                mostrarErroresFormato(ultimaPreview.errores());
                btnConfirmar.setDisable(true);
                return;
            }

            // Poblar tabla
            tablaPreview.setItems(FXCollections.observableArrayList(ultimaPreview.filas()));

            // Actualizar resumen
            int filasConError = (int) ultimaPreview.filas().stream().filter(f -> !f.valida()).count()
                    + (int) ultimaPreview.errores().stream()
                            .filter(e -> e.startsWith("Hoja '")).count();

            lblProductosNuevos.setText("Productos nuevos: " + ultimaPreview.productosNuevos());
            lblVentasARegistrar.setText("Ventas a registrar: " + ultimaPreview.ventasARegistrar());
            lblFilasConError.setText("Filas con error: " + filasConError);

            hboxResumenPreview.setVisible(true);
            hboxResumenPreview.setManaged(true);

            // Mostrar errores de hojas si los hay
            if (!ultimaPreview.errores().isEmpty()) {
                mostrarErroresFormato(ultimaPreview.errores());
            } else {
                ocultarErroresFormato();
            }

            // Habilitar confirmar solo si hay filas válidas
            boolean hayFilasValidas = ultimaPreview.filas().stream().anyMatch(FilaExcel::valida);
            btnConfirmar.setDisable(!hayFilasValidas);

            // Ocultar resultado previo
            txtResultado.setVisible(false);
            txtResultado.setManaged(false);

        } catch (Exception e) {
            log.error("Error al previsualizar Excel", e);
            mostrarAlerta(Alert.AlertType.ERROR, "Error al previsualizar",
                    "No se pudo leer el archivo: " + e.getMessage());
        }
    }

    @FXML
    private void onConfirmarImportacion() {
        if (archivoSeleccionado == null || ultimaPreview == null) return;

        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
        confirmacion.setTitle("Confirmar importación");
        confirmacion.setHeaderText("¿Confirmar importación?");
        confirmacion.setContentText(
                "Se crearán " + ultimaPreview.productosNuevos() + " producto(s) nuevo(s) y se registrarán "
                + ultimaPreview.ventasARegistrar() + " venta(s).\n\nEsta acción no se puede deshacer.");

        confirmacion.showAndWait().ifPresent(respuesta -> {
            if (respuesta == ButtonType.OK) {
                ejecutarImportacion();
            }
        });
    }

    @FXML
    private void onLimpiar() {
        archivoSeleccionado = null;
        ultimaPreview = null;
        txtRutaArchivo.clear();
        btnPrevisualizar.setDisable(true);
        btnConfirmar.setDisable(true);
        limpiarEstado();
    }

    // -------------------------------------------------------------------------
    // Lógica interna
    // -------------------------------------------------------------------------

    private void ejecutarImportacion() {
        try {
            btnConfirmar.setDisable(true);
            ImportResult resultado = excelImportService.importarExcel(archivoSeleccionado);

            // Mostrar resumen de resultado
            StringBuilder sb = new StringBuilder();
            sb.append("✔ Importación completada\n");
            sb.append("  Productos creados:   ").append(resultado.productosCreados()).append("\n");
            sb.append("  Ventas registradas:  ").append(resultado.ventasRegistradas()).append("\n");
            sb.append("  Filas con error:     ").append(resultado.filasConError()).append("\n");

            if (!resultado.detalleErrores().isEmpty()) {
                sb.append("\nDetalle de errores:\n");
                resultado.detalleErrores().stream().limit(10)
                        .forEach(e -> sb.append("  • ").append(e).append("\n"));
                if (resultado.detalleErrores().size() > 10) {
                    sb.append("  ... y ").append(resultado.detalleErrores().size() - 10).append(" más.\n");
                }
            }

            txtResultado.setText(sb.toString());
            txtResultado.setVisible(true);
            txtResultado.setManaged(true);

            log.info("Importación completada: {} productos, {} ventas, {} errores",
                    resultado.productosCreados(), resultado.ventasRegistradas(), resultado.filasConError());

        } catch (Exception e) {
            log.error("Error durante la importación", e);
            mostrarAlerta(Alert.AlertType.ERROR, "Error de importación",
                    "Ocurrió un error durante la importación: " + e.getMessage());
            btnConfirmar.setDisable(false);
        }
    }

    private void configurarColumnas() {
        // Columna estado: ✔ o ✘
        colEstado.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().valida() ? "✔" : "✘"));
        colEstado.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setStyle("✔".equals(item)
                            ? "-fx-text-fill: #28a745; -fx-font-weight: bold;"
                            : "-fx-text-fill: #dc3545; -fx-font-weight: bold;");
                }
            }
        });

        colArticulo.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().articulo() != null ? c.getValue().articulo() : ""));

        colPrecio.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().precioVenta() != null
                        ? "$" + String.format("%.2f", c.getValue().precioVenta())
                        : ""));

        colFechaHora.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().fechaHoraVenta() != null
                        ? c.getValue().fechaHoraVenta().format(FMT_DT)
                        : ""));

        colError.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().mensajeError() != null ? c.getValue().mensajeError() : ""));

        // Colorear filas inválidas
        tablaPreview.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(FilaExcel item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("");
                } else if (!item.valida()) {
                    setStyle("-fx-background-color: #f8d7da;");
                } else {
                    setStyle("");
                }
            }
        });
    }

    private void mostrarErroresFormato(List<String> errores) {
        txtErrores.setText(String.join("\n", errores));
        lblTituloErrores.setVisible(true);
        lblTituloErrores.setManaged(true);
        txtErrores.setVisible(true);
        txtErrores.setManaged(true);
    }

    private void ocultarErroresFormato() {
        lblTituloErrores.setVisible(false);
        lblTituloErrores.setManaged(false);
        txtErrores.setVisible(false);
        txtErrores.setManaged(false);
    }

    private void limpiarEstado() {
        tablaPreview.setItems(FXCollections.emptyObservableList());
        hboxResumenPreview.setVisible(false);
        hboxResumenPreview.setManaged(false);
        ocultarErroresFormato();
        txtResultado.setVisible(false);
        txtResultado.setManaged(false);
        txtResultado.clear();
    }

    private void mostrarAlerta(Alert.AlertType tipo, String titulo, String mensaje) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }
}
