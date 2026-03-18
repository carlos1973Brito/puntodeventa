package com.pos.controller;

import com.pos.config.DatabaseConfig;
import com.pos.dto.ProductoDTO;
import com.pos.exception.CodigoBarrasDuplicadoException;
import com.pos.exception.PosException;
import com.pos.model.Producto;
import com.pos.model.Usuario;
import com.pos.repository.impl.ProductoRepositoryImpl;
import com.pos.service.ProductoService;
import com.pos.service.impl.ProductoServiceImpl;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * Controlador de la vista de Productos (CRUD).
 */
public class ProductoController implements MainController.UsuarioAwareController {

    private static final Logger log = LoggerFactory.getLogger(ProductoController.class);
    private static final NumberFormat CURRENCY = NumberFormat.getCurrencyInstance(new Locale("es", "MX"));

    @FXML private TextField txtBusqueda;
    @FXML private Label lblAlertaStock;
    @FXML private TableView<Producto> tablaProductos;
    @FXML private TableColumn<Producto, String> colNombre;
    @FXML private TableColumn<Producto, String> colCodigo;
    @FXML private TableColumn<Producto, String> colCategoria;
    @FXML private TableColumn<Producto, String> colPrecioVenta;
    @FXML private TableColumn<Producto, String> colPrecioCosto;
    @FXML private TableColumn<Producto, String> colStock;
    @FXML private TableColumn<Producto, String> colStockMin;
    @FXML private TableColumn<Producto, String> colActivo;
    @FXML private TableColumn<Producto, Void> colAcciones;

    private ProductoService productoService;

    @FXML
    public void initialize() {
        SessionFactory sf = DatabaseConfig.buildSessionFactory();
        productoService = new ProductoServiceImpl(
                new ProductoRepositoryImpl(sf),
                sf
        );
        configurarTabla();
        cargarProductos();
    }

    @Override
    public void setUsuarioActual(Usuario usuario) {
        // no se requiere en esta vista
    }

    // -------------------------------------------------------------------------
    // Acciones
    // -------------------------------------------------------------------------

    @FXML
    private void onBuscar() {
        String termino = txtBusqueda.getText().trim();
        if (termino.isEmpty()) {
            cargarProductos();
            return;
        }
        List<Producto> resultado = productoService.buscarPorNombreOCategoria(termino);
        tablaProductos.setItems(FXCollections.observableArrayList(resultado));
    }

    @FXML
    private void onMostrarTodos() {
        txtBusqueda.clear();
        cargarProductos();
    }

    @FXML
    private void onNuevo() {
        mostrarFormulario(null);
    }

    // -------------------------------------------------------------------------
    // Formulario de producto
    // -------------------------------------------------------------------------

    private void mostrarFormulario(Producto productoEditar) {
        boolean esNuevo = productoEditar == null;
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(esNuevo ? "Nuevo Producto" : "Editar Producto");

        TextField txtNombre = new TextField(esNuevo ? "" : productoEditar.getNombre());
        TextField txtCodigo = new TextField(esNuevo ? "" : nvl(productoEditar.getCodigoBarras()));
        TextField txtDescripcion = new TextField(esNuevo ? "" : nvl(productoEditar.getDescripcion()));
        TextField txtPrecioVenta = new TextField(esNuevo ? "" : productoEditar.getPrecioVenta().toPlainString());
        TextField txtPrecioCosto = new TextField(esNuevo ? "0" : productoEditar.getPrecioCosto().toPlainString());
        TextField txtStockInicial = new TextField(esNuevo ? "0" : String.valueOf(productoEditar.getStockActual()));
        TextField txtStockMinimo = new TextField(esNuevo ? "5" : String.valueOf(productoEditar.getStockMinimo()));

        Label lblError = new Label();
        lblError.setStyle("-fx-text-fill: red; -fx-font-size: 12px;");

        Button btnGuardar = new Button(esNuevo ? "Crear" : "Guardar");
        btnGuardar.setStyle("-fx-background-color: #4a90d9; -fx-text-fill: white;");
        Button btnCancelar = new Button("Cancelar");

        btnGuardar.setOnAction(e -> {
            try {
                BigDecimal precioVenta = new BigDecimal(txtPrecioVenta.getText().trim());
                BigDecimal precioCosto = new BigDecimal(txtPrecioCosto.getText().trim());
                int stockInicial = Integer.parseInt(txtStockInicial.getText().trim());
                int stockMinimo = Integer.parseInt(txtStockMinimo.getText().trim());

                ProductoDTO dto = new ProductoDTO(
                        txtNombre.getText().trim(),
                        txtDescripcion.getText().trim(),
                        txtCodigo.getText().trim().isEmpty() ? null : txtCodigo.getText().trim(),
                        precioVenta, precioCosto, stockInicial, stockMinimo, null
                );

                if (esNuevo) {
                    productoService.crear(dto);
                } else {
                    productoService.actualizar(productoEditar.getId(), dto);
                }
                dialog.close();
                cargarProductos();
            } catch (CodigoBarrasDuplicadoException ex) {
                lblError.setText("Código de barras ya existe: " + ex.getCodigoBarras());
            } catch (PosException ex) {
                lblError.setText(ex.getMessage());
            } catch (NumberFormatException ex) {
                lblError.setText("Verifique que precios y stock sean números válidos.");
            }
        });

        btnCancelar.setOnAction(e -> dialog.close());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        int row = 0;
        grid.add(new Label("Nombre *:"), 0, row); grid.add(txtNombre, 1, row++);
        grid.add(new Label("Código de barras:"), 0, row); grid.add(txtCodigo, 1, row++);
        grid.add(new Label("Descripción:"), 0, row); grid.add(txtDescripcion, 1, row++);
        grid.add(new Label("Precio venta *:"), 0, row); grid.add(txtPrecioVenta, 1, row++);
        grid.add(new Label("Precio costo:"), 0, row); grid.add(txtPrecioCosto, 1, row++);
        grid.add(new Label("Stock inicial:"), 0, row); grid.add(txtStockInicial, 1, row++);
        grid.add(new Label("Stock mínimo:"), 0, row); grid.add(txtStockMinimo, 1, row++);
        grid.add(lblError, 0, row++, 2, 1);
        grid.add(new HBox(10, btnGuardar, btnCancelar), 0, row, 2, 1);

        dialog.setScene(new Scene(grid, 380, 360));
        dialog.showAndWait();
    }

    // -------------------------------------------------------------------------
    // Tabla
    // -------------------------------------------------------------------------

    private void configurarTabla() {
        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        colCodigo.setCellValueFactory(c -> new SimpleStringProperty(nvl(c.getValue().getCodigoBarras())));
        colCategoria.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getCategoria() != null ? c.getValue().getCategoria().getNombre() : "—"));
        colPrecioVenta.setCellValueFactory(c ->
                new SimpleStringProperty(CURRENCY.format(c.getValue().getPrecioVenta())));
        colPrecioCosto.setCellValueFactory(c ->
                new SimpleStringProperty(CURRENCY.format(c.getValue().getPrecioCosto())));
        colStock.setCellValueFactory(c ->
                new SimpleStringProperty(String.valueOf(c.getValue().getStockActual())));
        colStockMin.setCellValueFactory(c ->
                new SimpleStringProperty(String.valueOf(c.getValue().getStockMinimo())));
        colActivo.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().isActivo() ? "✔" : "✘"));

        // Colorear filas con bajo stock
        tablaProductos.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(Producto p, boolean empty) {
                super.updateItem(p, empty);
                if (!empty && p != null && p.getStockActual() <= p.getStockMinimo()) {
                    setStyle("-fx-background-color: #fff3cd;");
                } else {
                    setStyle("");
                }
            }
        });

        // Botones editar / desactivar
        colAcciones.setCellFactory(col -> new TableCell<>() {
            private final Button btnEditar = new Button("✏");
            private final Button btnDesactivar = new Button("🗑");
            private final HBox hbox = new HBox(6, btnEditar, btnDesactivar);
            {
                btnEditar.setStyle("-fx-background-color: #4a90d9; -fx-text-fill: white; -fx-background-radius: 4;");
                btnDesactivar.setStyle("-fx-background-color: #dc3545; -fx-text-fill: white; -fx-background-radius: 4;");
                hbox.setAlignment(Pos.CENTER);

                btnEditar.setOnAction(e -> {
                    Producto p = getTableView().getItems().get(getIndex());
                    mostrarFormulario(p);
                });
                btnDesactivar.setOnAction(e -> {
                    Producto p = getTableView().getItems().get(getIndex());
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                            "¿Desactivar el producto '" + p.getNombre() + "'?",
                            ButtonType.YES, ButtonType.NO);
                    confirm.setHeaderText(null);
                    confirm.showAndWait().ifPresent(btn -> {
                        if (btn == ButtonType.YES) {
                            try {
                                productoService.desactivar(p.getId());
                                cargarProductos();
                            } catch (PosException ex) {
                                log.error("Error al desactivar producto", ex);
                            }
                        }
                    });
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : hbox);
            }
        });
    }

    private void cargarProductos() {
        List<Producto> productos = productoService.listarActivos();
        tablaProductos.setItems(FXCollections.observableArrayList(productos));

        List<Producto> bajoStock = productoService.listarBajoStockMinimo();
        if (!bajoStock.isEmpty()) {
            lblAlertaStock.setText("⚠ " + bajoStock.size() + " producto(s) con bajo stock");
            lblAlertaStock.setVisible(true);
            lblAlertaStock.setManaged(true);
        } else {
            lblAlertaStock.setVisible(false);
            lblAlertaStock.setManaged(false);
        }
    }

    private String nvl(String s) {
        return s != null ? s : "—";
    }
}
