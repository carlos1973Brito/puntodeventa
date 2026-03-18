package com.pos.controller;

import com.pos.config.DatabaseConfig;
import com.pos.dto.ProductoVendidoDTO;
import com.pos.dto.ReporteInventarioDTO;
import com.pos.dto.ReporteVentasDTO;
import com.pos.model.Producto;
import com.pos.model.Usuario;
import com.pos.repository.impl.ProductoRepositoryImpl;
import com.pos.repository.impl.VentaRepositoryImpl;
import com.pos.service.PdfExportService;
import com.pos.service.ReporteService;
import com.pos.service.impl.PdfExportServiceImpl;
import com.pos.service.impl.ReporteServiceImpl;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.ToggleGroup;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Controlador de la vista de Reportes.
 * Permite generar reportes de ventas e inventario y exportarlos a PDF.
 * Requisitos: 5.1, 5.2, 5.3, 5.4, 5.5, 6.1, 6.2, 6.3, 6.4
 */
public class ReporteController implements MainController.UsuarioAwareController {

    private static final Logger log = LoggerFactory.getLogger(ReporteController.class);

    // --- Controles de filtro ---
    @FXML private RadioButton rbVentas;
    @FXML private RadioButton rbInventario;
    @FXML private Label lblDesde;
    @FXML private Label lblHasta;
    @FXML private DatePicker dpDesde;
    @FXML private DatePicker dpHasta;
    @FXML private Button btnExportar;
    @FXML private Label lblResumen;

    // --- Tabs ---
    @FXML private TabPane tabPane;
    @FXML private Tab tabResumen;
    @FXML private Tab tabMasVendidos;
    @FXML private Tab tabInventario;

    // --- Tab Resumen ---
    @FXML private TextArea txtResumen;

    // --- Tab Más Vendidos ---
    @FXML private TableView<ProductoVendidoDTO> tablaMasVendidos;
    @FXML private TableColumn<ProductoVendidoDTO, String> colMvProducto;
    @FXML private TableColumn<ProductoVendidoDTO, String> colMvCantidad;
    @FXML private TableColumn<ProductoVendidoDTO, String> colMvTotal;

    // --- Tab Inventario ---
    @FXML private TableView<Producto> tablaInventario;
    @FXML private TableColumn<Producto, String> colInvNombre;
    @FXML private TableColumn<Producto, String> colInvStock;
    @FXML private TableColumn<Producto, String> colInvCosto;
    @FXML private TableColumn<Producto, String> colInvVenta;
    @FXML private TableColumn<Producto, String> colInvValor;
    @FXML private TableColumn<Producto, String> colInvEstado;

    // --- Estado ---
    private ReporteService reporteService;
    private PdfExportService pdfExportService;
    private ReporteVentasDTO ultimoReporteVentas;
    private ReporteInventarioDTO ultimoReporteInventario;

    @FXML
    public void initialize() {
        // Inicializar servicios
        var sf = DatabaseConfig.buildSessionFactory();
        reporteService = new ReporteServiceImpl(
                new VentaRepositoryImpl(sf),
                new ProductoRepositoryImpl(sf)
        );
        pdfExportService = new PdfExportServiceImpl();

        // Agrupar radio buttons (el ToggleGroup se define en código, no en FXML)
        ToggleGroup tgTipo = new ToggleGroup();
        rbVentas.setToggleGroup(tgTipo);
        rbInventario.setToggleGroup(tgTipo);
        rbVentas.setSelected(true);

        // Fechas por defecto: mes actual
        dpDesde.setValue(LocalDate.now().withDayOfMonth(1));
        dpHasta.setValue(LocalDate.now());

        // Configurar columnas de más vendidos
        colMvProducto.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().nombreProducto()));
        colMvCantidad.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().cantidadTotal())));
        colMvTotal.setCellValueFactory(c -> new SimpleStringProperty("$" + fmt(c.getValue().totalRecaudado())));

        // Configurar columnas de inventario
        colInvNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        colInvStock.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getStockActual())));
        colInvCosto.setCellValueFactory(c -> new SimpleStringProperty("$" + fmt(c.getValue().getPrecioCosto())));
        colInvVenta.setCellValueFactory(c -> new SimpleStringProperty("$" + fmt(c.getValue().getPrecioVenta())));
        colInvValor.setCellValueFactory(c -> {
            BigDecimal valor = c.getValue().getPrecioCosto()
                    .multiply(BigDecimal.valueOf(c.getValue().getStockActual()));
            return new SimpleStringProperty("$" + fmt(valor));
        });
        colInvEstado.setCellValueFactory(c -> {
            Producto p = c.getValue();
            String estado = p.getStockActual() <= p.getStockMinimo() ? "⚠ Bajo stock" : "OK";
            return new SimpleStringProperty(estado);
        });

        // Colorear filas de bajo stock en inventario
        tablaInventario.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(Producto item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("");
                } else if (item.getStockActual() <= item.getStockMinimo()) {
                    setStyle("-fx-background-color: #fff3cd;");
                } else {
                    setStyle("");
                }
            }
        });

        // Mostrar/ocultar controles de fecha según tipo de reporte
        rbVentas.selectedProperty().addListener((obs, old, val) -> {
            boolean visible = Boolean.TRUE.equals(val);
            lblDesde.setVisible(visible);
            lblHasta.setVisible(visible);
            dpDesde.setVisible(visible);
            dpHasta.setVisible(visible);
        });
    }

    @Override
    public void setUsuarioActual(Usuario usuario) {
        // no se requiere en esta vista
    }

    // -------------------------------------------------------------------------
    // Acciones
    // -------------------------------------------------------------------------

    @FXML
    private void onGenerar() {
        if (rbVentas.isSelected()) {
            generarReporteVentas();
        } else {
            generarReporteInventario();
        }
    }

    @FXML
    private void onExportarPDF() {
        try {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Guardar PDF");
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("PDF", "*.pdf"));
            chooser.setInitialFileName(rbVentas.isSelected() ? "reporte_ventas.pdf" : "reporte_inventario.pdf");

            File archivo = chooser.showSaveDialog(btnExportar.getScene().getWindow());
            if (archivo == null) return;

            byte[] pdf;
            if (rbVentas.isSelected() && ultimoReporteVentas != null) {
                pdf = pdfExportService.exportarReporteVentas(ultimoReporteVentas);
            } else if (!rbVentas.isSelected() && ultimoReporteInventario != null) {
                pdf = pdfExportService.exportarReporteInventario(ultimoReporteInventario);
            } else {
                mostrarAlerta(Alert.AlertType.WARNING, "Sin datos", "Genera un reporte primero.");
                return;
            }

            try (FileOutputStream fos = new FileOutputStream(archivo)) {
                fos.write(pdf);
            }

            lblResumen.setText("PDF exportado: " + archivo.getName());
            log.info("PDF exportado a {}", archivo.getAbsolutePath());

        } catch (Exception e) {
            log.error("Error al exportar PDF", e);
            mostrarAlerta(Alert.AlertType.ERROR, "Error", "No se pudo exportar el PDF: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Lógica interna
    // -------------------------------------------------------------------------

    private void generarReporteVentas() {
        LocalDate desde = dpDesde.getValue();
        LocalDate hasta = dpHasta.getValue();

        if (desde == null || hasta == null) {
            mostrarAlerta(Alert.AlertType.WARNING, "Fechas requeridas", "Selecciona el rango de fechas.");
            return;
        }
        if (desde.isAfter(hasta)) {
            mostrarAlerta(Alert.AlertType.WARNING, "Rango inválido", "La fecha 'Desde' no puede ser posterior a 'Hasta'.");
            return;
        }

        try {
            ultimoReporteVentas = reporteService.generarReporteVentas(desde, hasta);
            ultimoReporteInventario = null;

            // Resumen en texto
            StringBuilder sb = new StringBuilder();
            sb.append("=== REPORTE DE VENTAS ===\n");
            sb.append("Período: ").append(desde).append(" — ").append(hasta).append("\n\n");
            sb.append("Total de ventas:    ").append(ultimoReporteVentas.totalVentas()).append("\n");
            sb.append("Total recaudado:    $").append(fmt(ultimoReporteVentas.totalRecaudado())).append("\n\n");
            sb.append("--- Desglose por método de pago ---\n");
            if (ultimoReporteVentas.porMetodoPago() != null) {
                for (Map.Entry<?, BigDecimal> e : ultimoReporteVentas.porMetodoPago().entrySet()) {
                    sb.append(String.format("  %-20s $%s%n", e.getKey(), fmt(e.getValue())));
                }
            }
            sb.append("\n--- Ventas por categoría ---\n");
            if (ultimoReporteVentas.porCategoria() != null) {
                for (Map.Entry<String, BigDecimal> e : ultimoReporteVentas.porCategoria().entrySet()) {
                    sb.append(String.format("  %-20s $%s%n", e.getKey(), fmt(e.getValue())));
                }
            }
            txtResumen.setText(sb.toString());

            // Tabla más vendidos
            if (ultimoReporteVentas.masVendidos() != null) {
                tablaMasVendidos.setItems(FXCollections.observableArrayList(ultimoReporteVentas.masVendidos()));
            }

            // Limpiar tabla inventario
            tablaInventario.setItems(FXCollections.emptyObservableList());

            lblResumen.setText("Reporte generado: " + ultimoReporteVentas.totalVentas()
                    + " ventas | $" + fmt(ultimoReporteVentas.totalRecaudado()) + " recaudados");
            btnExportar.setDisable(false);
            tabPane.getSelectionModel().select(tabResumen);

        } catch (Exception e) {
            log.error("Error al generar reporte de ventas", e);
            mostrarAlerta(Alert.AlertType.ERROR, "Error", "No se pudo generar el reporte: " + e.getMessage());
        }
    }

    private void generarReporteInventario() {
        try {
            ultimoReporteInventario = reporteService.generarReporteInventario();
            ultimoReporteVentas = null;

            // Resumen en texto
            StringBuilder sb = new StringBuilder();
            sb.append("=== REPORTE DE INVENTARIO ===\n\n");
            sb.append("Productos activos:  ").append(ultimoReporteInventario.productosActivos().size()).append("\n");
            sb.append("Valor total:        $").append(fmt(ultimoReporteInventario.valorTotalInventario())).append("\n");
            sb.append("Bajo stock mínimo:  ").append(ultimoReporteInventario.productosBajoStock().size()).append(" producto(s)\n");
            txtResumen.setText(sb.toString());

            // Tabla inventario
            tablaInventario.setItems(FXCollections.observableArrayList(ultimoReporteInventario.productosActivos()));

            // Limpiar tabla más vendidos
            tablaMasVendidos.setItems(FXCollections.emptyObservableList());

            lblResumen.setText("Inventario: " + ultimoReporteInventario.productosActivos().size()
                    + " productos | Valor total: $" + fmt(ultimoReporteInventario.valorTotalInventario()));
            btnExportar.setDisable(false);
            tabPane.getSelectionModel().select(tabInventario);

        } catch (Exception e) {
            log.error("Error al generar reporte de inventario", e);
            mostrarAlerta(Alert.AlertType.ERROR, "Error", "No se pudo generar el reporte: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Utilidades
    // -------------------------------------------------------------------------

    private String fmt(BigDecimal value) {
        if (value == null) return "0.00";
        return String.format("%.2f", value);
    }

    private void mostrarAlerta(Alert.AlertType tipo, String titulo, String mensaje) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }
}
