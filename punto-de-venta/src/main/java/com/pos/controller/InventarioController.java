package com.pos.controller;

import com.pos.config.DatabaseConfig;
import com.pos.model.MovimientoStock;
import com.pos.model.Producto;
import com.pos.model.Usuario;
import com.pos.repository.impl.MovimientoStockRepositoryImpl;
import com.pos.repository.impl.ProductoRepositoryImpl;
import com.pos.repository.impl.UsuarioRepositoryImpl;
import com.pos.service.ProductoService;
import com.pos.service.StockService;
import com.pos.service.impl.ProductoServiceImpl;
import com.pos.service.impl.StockServiceImpl;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Controlador de la vista de Inventario (movimientos de stock).
 */
public class InventarioController implements MainController.UsuarioAwareController {

    private static final Logger log = LoggerFactory.getLogger(InventarioController.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final NumberFormat CURRENCY = NumberFormat.getCurrencyInstance(new Locale("es", "MX"));

    // --- Pestaña Stock Actual ---
    @FXML private TextField txtFiltroStock;
    @FXML private TableView<Producto> tablaStock;
    @FXML private TableColumn<Producto, String> colStockNombre;
    @FXML private TableColumn<Producto, String> colStockCategoria;
    @FXML private TableColumn<Producto, String> colStockActual;
    @FXML private TableColumn<Producto, String> colStockMinimo;
    @FXML private TableColumn<Producto, String> colStockPrecio;
    @FXML private TableColumn<Producto, String> colStockEstado;

    // --- Pestaña Movimientos ---
    @FXML private ComboBox<Producto> cmbProducto;
    @FXML private TableView<MovimientoStock> tablaMovimientos;
    @FXML private TableColumn<MovimientoStock, String> colFecha;
    @FXML private TableColumn<MovimientoStock, String> colProductoMov;
    @FXML private TableColumn<MovimientoStock, String> colTipo;
    @FXML private TableColumn<MovimientoStock, String> colCantidad;
    @FXML private TableColumn<MovimientoStock, String> colStockAnterior;
    @FXML private TableColumn<MovimientoStock, String> colStockNuevo;
    @FXML private TableColumn<MovimientoStock, String> colJustificacion;
    @FXML private TableColumn<MovimientoStock, String> colUsuarioMov;

    private StockService stockService;
    private ProductoService productoService;
    private Usuario usuarioActual;
    private ObservableList<Producto> todosLosProductos;

    @FXML
    public void initialize() {
        try {
            SessionFactory sf = DatabaseConfig.buildSessionFactory();
            MovimientoStockRepositoryImpl movRepo = new MovimientoStockRepositoryImpl(sf);
            ProductoRepositoryImpl prodRepo = new ProductoRepositoryImpl(sf);
            UsuarioRepositoryImpl usuarioRepo = new UsuarioRepositoryImpl(sf);

            stockService = new StockServiceImpl(prodRepo, movRepo, usuarioRepo);
            productoService = new ProductoServiceImpl(prodRepo, sf);

            configurarTablaStock();
            configurarTabla();
            cargarProductosCombo();
            cargarStockActual();
        } catch (Exception e) {
            log.error("Error en InventarioController.initialize()", e);
            throw e;
        }
    }

    @Override
    public void setUsuarioActual(Usuario usuario) {
        this.usuarioActual = usuario;
    }

    // -------------------------------------------------------------------------
    // Acciones
    // -------------------------------------------------------------------------

    @FXML
    private void onActualizarStock() {
        cargarStockActual();
    }

    @FXML
    private void onFiltrarStock() {
        String filtro = txtFiltroStock.getText().trim().toLowerCase();
        if (todosLosProductos == null) return;
        if (filtro.isEmpty()) {
            tablaStock.setItems(todosLosProductos);
            return;
        }
        FilteredList<Producto> filtrados = new FilteredList<>(todosLosProductos,
                p -> p.getNombre().toLowerCase().contains(filtro)
                        || (p.getCategoria() != null && p.getCategoria().getNombre().toLowerCase().contains(filtro)));
        tablaStock.setItems(filtrados);
    }

    @FXML
    private void onFiltrarProducto() {
        Producto seleccionado = cmbProducto.getValue();
        if (seleccionado == null) return;
        List<MovimientoStock> movimientos = stockService.historialPorProducto(seleccionado.getId());
        tablaMovimientos.setItems(FXCollections.observableArrayList(movimientos));
    }

    @FXML
    private void onVerTodos() {
        cmbProducto.setValue(null);
        // Cargar todos los movimientos de todos los productos
        List<Producto> productos = productoService.listarActivos();
        List<MovimientoStock> todos = productos.stream()
                .flatMap(p -> stockService.historialPorProducto(p.getId()).stream())
                .sorted((a, b) -> b.getFecha().compareTo(a.getFecha()))
                .toList();
        tablaMovimientos.setItems(FXCollections.observableArrayList(todos));
    }

    @FXML
    private void onEntrada() {
        mostrarDialogoMovimiento(false);
    }

    @FXML
    private void onAjuste() {
        mostrarDialogoMovimiento(true);
    }

    // -------------------------------------------------------------------------
    // Diálogo de movimiento
    // -------------------------------------------------------------------------

    private void mostrarDialogoMovimiento(boolean esAjuste) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(esAjuste ? "Ajuste Manual de Stock" : "Entrada de Mercancía");

        ComboBox<Producto> cmbProd = new ComboBox<>();
        cmbProd.setItems(FXCollections.observableArrayList(productoService.listarActivos()));
        cmbProd.setPromptText("Seleccionar producto...");
        cmbProd.setPrefWidth(250);

        Label lblStockActual = new Label("Stock actual: —");
        cmbProd.setOnAction(e -> {
            Producto p = cmbProd.getValue();
            if (p != null) lblStockActual.setText("Stock actual: " + p.getStockActual());
        });

        TextField txtCantidad = new TextField();
        txtCantidad.setPromptText(esAjuste ? "Nuevo stock total" : "Cantidad a ingresar");

        TextField txtJustificacion = new TextField();
        txtJustificacion.setPromptText("Motivo / justificación");

        Label lblError = new Label();
        lblError.setStyle("-fx-text-fill: red;");

        Button btnGuardar = new Button(esAjuste ? "Aplicar Ajuste" : "Registrar Entrada");
        btnGuardar.setStyle("-fx-background-color: #28a745; -fx-text-fill: white;");
        Button btnCancelar = new Button("Cancelar");

        btnGuardar.setOnAction(e -> {
            Producto prod = cmbProd.getValue();
            if (prod == null) { lblError.setText("Seleccione un producto."); return; }
            String justificacion = txtJustificacion.getText().trim();
            if (justificacion.isEmpty()) { lblError.setText("Ingrese una justificación."); return; }

            try {
                int cantidad = Integer.parseInt(txtCantidad.getText().trim());
                Long usuarioId = usuarioActual != null ? usuarioActual.getId() : null;

                if (esAjuste) {
                    stockService.registrarAjuste(prod.getId(), cantidad, justificacion, usuarioId);
                } else {
                    stockService.registrarEntrada(prod.getId(), cantidad, justificacion, usuarioId);
                }
                dialog.close();
                onVerTodos();
                cargarProductosCombo();
            } catch (NumberFormatException ex) {
                lblError.setText("Ingrese una cantidad válida.");
            }
        });

        btnCancelar.setOnAction(e -> dialog.close());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.add(new Label("Producto:"), 0, 0); grid.add(cmbProd, 1, 0);
        grid.add(new Label(""), 0, 1); grid.add(lblStockActual, 1, 1);
        grid.add(new Label(esAjuste ? "Nuevo stock:" : "Cantidad:"), 0, 2);
        grid.add(txtCantidad, 1, 2);
        grid.add(new Label("Justificación:"), 0, 3); grid.add(txtJustificacion, 1, 3);
        grid.add(lblError, 0, 4, 2, 1);
        grid.add(new HBox(10, btnGuardar, btnCancelar), 0, 5, 2, 1);

        dialog.setScene(new Scene(grid, 400, 260));
        dialog.showAndWait();
    }

    // -------------------------------------------------------------------------
    // Utilidades
    // -------------------------------------------------------------------------

    private void configurarTablaStock() {
        colStockNombre.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getNombre()));
        colStockCategoria.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getCategoria() != null
                        ? c.getValue().getCategoria().getNombre() : "Sin categoría"));
        colStockActual.setCellValueFactory(c ->
                new SimpleStringProperty(String.valueOf(c.getValue().getStockActual())));
        colStockMinimo.setCellValueFactory(c ->
                new SimpleStringProperty(String.valueOf(c.getValue().getStockMinimo())));
        colStockPrecio.setCellValueFactory(c ->
                new SimpleStringProperty(CURRENCY.format(
                        c.getValue().getPrecioVenta() != null ? c.getValue().getPrecioVenta() : BigDecimal.ZERO)));
        colStockEstado.setCellValueFactory(c -> {
            Producto p = c.getValue();
            String estado = p.getStockActual() <= p.getStockMinimo() ? "⚠ Bajo" : "✓ OK";
            return new SimpleStringProperty(estado);
        });
        // Colorear filas con stock bajo
        tablaStock.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(Producto p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) {
                    setStyle("");
                } else if (p.getStockActual() <= p.getStockMinimo()) {
                    setStyle("-fx-background-color: #fff3cd;");
                } else {
                    setStyle("");
                }
            }
        });
    }

    private void cargarStockActual() {
        List<Producto> productos = productoService.listarActivos();
        todosLosProductos = FXCollections.observableArrayList(productos);
        tablaStock.setItems(todosLosProductos);
        log.info("[inventario] Stock cargado: {} productos", productos.size());
    }

    private void configurarTabla() {
        colFecha.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getFecha() != null
                        ? c.getValue().getFecha().format(FMT) : "—"));
        colProductoMov.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getProducto() != null
                        ? c.getValue().getProducto().getNombre() : "—"));
        colTipo.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getTipo().name()));
        colCantidad.setCellValueFactory(c ->
                new SimpleStringProperty(String.valueOf(c.getValue().getCantidad())));
        colStockAnterior.setCellValueFactory(c ->
                new SimpleStringProperty(String.valueOf(c.getValue().getStockAnterior())));
        colStockNuevo.setCellValueFactory(c ->
                new SimpleStringProperty(String.valueOf(c.getValue().getStockNuevo())));
        colJustificacion.setCellValueFactory(c ->
                new SimpleStringProperty(nvl(c.getValue().getJustificacion())));
        colUsuarioMov.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getUsuario() != null
                        ? c.getValue().getUsuario().getNombreCompleto() : "—"));
    }

    private void cargarProductosCombo() {
        List<Producto> productos = productoService.listarActivos();
        cmbProducto.setItems(FXCollections.observableArrayList(productos));
        // Mostrar nombre en el combo
        cmbProducto.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Producto p, boolean empty) {
                super.updateItem(p, empty);
                setText(empty || p == null ? null : p.getNombre() + " (stock: " + p.getStockActual() + ")");
            }
        });
        cmbProducto.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Producto p, boolean empty) {
                super.updateItem(p, empty);
                setText(empty || p == null ? null : p.getNombre());
            }
        });
    }

    private String nvl(String s) { return s != null ? s : "—"; }
}
