package com.pos.controller;

import com.pos.config.DatabaseConfig;
import com.pos.service.ConfiguracionService;
import com.pos.service.TicketPrinterService;
import com.pos.service.impl.TicketPrinterServiceImpl;
import com.pos.exception.PagoInsuficienteException;
import com.pos.exception.PosException;
import com.pos.exception.StockInsuficienteException;
import com.pos.exception.VentaVaciaException;
import com.pos.model.LineaVenta;
import com.pos.model.Usuario;
import com.pos.model.Venta;
import com.pos.model.enums.MetodoPago;
import com.pos.repository.impl.MovimientoStockRepositoryImpl;
import com.pos.model.Producto;
import com.pos.repository.impl.ProductoRepositoryImpl;
import com.pos.service.ProductoService;
import com.pos.service.impl.ProductoServiceImpl;
import com.pos.repository.impl.UsuarioRepositoryImpl;
import com.pos.repository.impl.VentaRepositoryImpl;
import com.pos.service.VentaService;
import com.pos.service.impl.VentaServiceImpl;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
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
 * Controlador de la pantalla de ventas (POS).
 */
public class VentaController implements MainController.UsuarioAwareController {

    private static final Logger log = LoggerFactory.getLogger(VentaController.class);
    private static final NumberFormat CURRENCY = NumberFormat.getCurrencyInstance(new Locale("es", "MX"));

    @FXML private TextField txtBusqueda;
    @FXML private TextField txtCantidad;
    @FXML private Label lblBusquedaError;
    @FXML private Label lblVentaId;
    @FXML private Label lblTotal;

    @FXML private TableView<LineaVenta> tablaLineas;
    @FXML private TableColumn<LineaVenta, String> colProducto;
    @FXML private TableColumn<LineaVenta, String> colPrecio;
    @FXML private TableColumn<LineaVenta, String> colCantidad;
    @FXML private TableColumn<LineaVenta, String> colSubtotal;
    @FXML private TableColumn<LineaVenta, Void> colAccion;

    private VentaService ventaService;
    private ProductoService productoService;
    private Usuario usuarioActual;
    private Venta ventaActual;
    private ContextMenu autoCompleteMenu;
    private Long productoSeleccionadoId;
    private final TicketPrinterService ticketPrinter = new TicketPrinterServiceImpl();
    private final ConfiguracionService configuracion = new ConfiguracionService();

    @FXML
    public void initialize() {
        try {
            SessionFactory sf = DatabaseConfig.buildSessionFactory();
            ventaService = new VentaServiceImpl(
                    new VentaRepositoryImpl(sf),
                    new ProductoRepositoryImpl(sf),
                    new MovimientoStockRepositoryImpl(sf),
                    new UsuarioRepositoryImpl(sf)
            );
            productoService = new ProductoServiceImpl(new ProductoRepositoryImpl(sf), sf);
            autoCompleteMenu = new ContextMenu();
            configurarTabla();
            configurarAutoComplete();
        } catch (Exception e) {
            log.error("Error en VentaController.initialize()", e);
            throw e; // re-lanzar para que JavaFX muestre el error real
        }
    }

    @Override
    public void setUsuarioActual(Usuario usuario) {
        this.usuarioActual = usuario;
        iniciarNuevaVenta();
    }

    // -------------------------------------------------------------------------
    // Acciones
    // -------------------------------------------------------------------------

    @FXML
    private void onAgregarProducto() {
        String busqueda = txtBusqueda.getText().trim();
        if (busqueda.isEmpty()) return;

        // Si no hay producto seleccionado del autocompletado y el texto es muy corto,
        // no intentar buscar — esperar a que el usuario seleccione del menú
        if (productoSeleccionadoId == null && busqueda.length() < 3
                && !busqueda.matches(".*\\d.*")) { // permitir códigos de barras cortos con números
            mostrarError("Selecciona un producto del menú o escribe el código de barras completo.");
            return;
        }

        int cantidad = 1;
        String cantStr = txtCantidad.getText().trim();
        if (!cantStr.isEmpty()) {
            try {
                cantidad = Integer.parseInt(cantStr);
                if (cantidad <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                mostrarError("Cantidad inválida.");
                return;
            }
        }

        if (ventaActual == null) {
            iniciarNuevaVenta();
        }

        try {
            // Si hay un producto seleccionado del autocompletado, usar su ID directamente
            if (productoSeleccionadoId != null) {
                ventaActual = ventaService.agregarLineaPorId(ventaActual.getId(), productoSeleccionadoId, cantidad);
                productoSeleccionadoId = null;
            } else {
                ventaActual = ventaService.agregarLinea(ventaActual.getId(), busqueda, cantidad);
            }
            actualizarTabla();
            ocultarError();
            txtBusqueda.clear();
            txtCantidad.clear();
            txtBusqueda.requestFocus();
        } catch (StockInsuficienteException e) {
            mostrarError("Stock insuficiente. Disponible: " + e.getStockDisponible());
        } catch (PosException e) {
            mostrarError(e.getMessage());
        }
    }

    @FXML
    private void onCobrar() {
        if (ventaActual == null || ventaActual.getLineas().isEmpty()) {
            mostrarError("Agregue al menos un producto antes de cobrar.");
            return;
        }
        mostrarDialogoPago();
    }

    @FXML
    private void onCancelarVenta() {
        if (ventaActual == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "¿Cancelar la venta actual?", ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Cancelar Venta");
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                try {
                    ventaService.cancelarVenta(ventaActual.getId());
                } catch (PosException e) {
                    log.warn("Error al cancelar venta: {}", e.getMessage());
                }
                iniciarNuevaVenta();
            }
        });
    }

    // -------------------------------------------------------------------------
    // Diálogo de pago
    // -------------------------------------------------------------------------

    private void mostrarDialogoPago() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Cobrar Venta");

        final BigDecimal totalOriginal = ventaActual.getTotal();
        // totalACobrar es mutable via array para usarlo en lambdas
        final BigDecimal[] totalACobrar = {totalOriginal};

        Label lblTotalOriginal = new Label("Subtotal: " + CURRENCY.format(totalOriginal));
        lblTotalOriginal.setStyle("-fx-font-size: 14px; -fx-text-fill: #555;");

        Label lblTotalFinal = new Label("Total a cobrar: " + CURRENCY.format(totalOriginal));
        lblTotalFinal.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");

        // --- Descuento ---
        ToggleGroup tgDescuento = new ToggleGroup();
        RadioButton rbDescMonto = new RadioButton("$ Monto");
        RadioButton rbDescPct = new RadioButton("% Porcentaje");
        rbDescMonto.setToggleGroup(tgDescuento);
        rbDescPct.setToggleGroup(tgDescuento);
        rbDescMonto.setSelected(true);

        TextField txtDescuento = new TextField("0");
        txtDescuento.setPrefWidth(100);
        txtDescuento.setPromptText("0");

        Label lblDescAplicado = new Label("Descuento: $0.00");
        lblDescAplicado.setStyle("-fx-text-fill: #e67e22; -fx-font-weight: bold;");

        // Recalcular total cuando cambia el descuento
        Runnable recalcular = () -> {
            try {
                BigDecimal valor = new BigDecimal(txtDescuento.getText().trim());
                BigDecimal descuento;
                if (rbDescPct.isSelected()) {
                    // porcentaje: no puede superar 100%
                    if (valor.compareTo(BigDecimal.valueOf(100)) > 0) valor = BigDecimal.valueOf(100);
                    descuento = totalOriginal.multiply(valor).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
                } else {
                    // monto fijo: no puede superar el total
                    if (valor.compareTo(totalOriginal) > 0) valor = totalOriginal;
                    descuento = valor;
                }
                totalACobrar[0] = totalOriginal.subtract(descuento);
                lblDescAplicado.setText("Descuento: " + CURRENCY.format(descuento));
                lblTotalFinal.setText("Total a cobrar: " + CURRENCY.format(totalACobrar[0]));
            } catch (NumberFormatException ignored) {
                totalACobrar[0] = totalOriginal;
                lblDescAplicado.setText("Descuento: —");
                lblTotalFinal.setText("Total a cobrar: " + CURRENCY.format(totalOriginal));
            }
        };

        txtDescuento.textProperty().addListener((obs, o, n) -> recalcular.run());
        tgDescuento.selectedToggleProperty().addListener((obs, o, n) -> recalcular.run());

        // --- Método de pago ---
        ToggleGroup tgMetodo = new ToggleGroup();
        RadioButton rbEfectivo = new RadioButton("Efectivo");
        RadioButton rbTarjetaCredito = new RadioButton("Tarjeta Crédito");
        RadioButton rbTarjetaDebito = new RadioButton("Tarjeta Débito");
        RadioButton rbCredito = new RadioButton("Crédito/Apartado");
        rbEfectivo.setToggleGroup(tgMetodo);
        rbTarjetaCredito.setToggleGroup(tgMetodo);
        rbTarjetaDebito.setToggleGroup(tgMetodo);
        rbCredito.setToggleGroup(tgMetodo);
        rbEfectivo.setSelected(true);

        HBox hbMetodo = new HBox(12, rbEfectivo, rbTarjetaCredito, rbTarjetaDebito, rbCredito);
        hbMetodo.setAlignment(Pos.CENTER_LEFT);

        // Monto recibido (solo efectivo)
        Label lblMonto = new Label("Monto recibido:");
        TextField txtMonto = new TextField();
        txtMonto.setPromptText("0.00");
        txtMonto.setPrefWidth(150);

        // Nombre cliente (solo crédito)
        Label lblCliente = new Label("Nombre cliente:");
        TextField txtCliente = new TextField();
        txtCliente.setPromptText("Nombre del cliente");
        txtCliente.setPrefWidth(200);
        lblCliente.setVisible(false); lblCliente.setManaged(false);
        txtCliente.setVisible(false); txtCliente.setManaged(false);

        Label lblCambio = new Label("Cambio: $0.00");
        lblCambio.setStyle("-fx-font-size: 16px; -fx-text-fill: #28a745;");

        // Calcular cambio en tiempo real
        txtMonto.textProperty().addListener((obs, old, val) -> {
            try {
                BigDecimal monto = new BigDecimal(val.trim());
                BigDecimal cambio = monto.subtract(totalACobrar[0]);
                if (cambio.compareTo(BigDecimal.ZERO) >= 0) {
                    lblCambio.setText("Cambio: " + CURRENCY.format(cambio));
                    lblCambio.setStyle("-fx-font-size: 16px; -fx-text-fill: #28a745;");
                } else {
                    lblCambio.setText("Falta: " + CURRENCY.format(cambio.abs()));
                    lblCambio.setStyle("-fx-font-size: 16px; -fx-text-fill: #dc3545;");
                }
            } catch (NumberFormatException ignored) {
                lblCambio.setText("Cambio: —");
            }
        });

        // Ocultar monto/cambio para tarjeta
        tgMetodo.selectedToggleProperty().addListener((obs, old, val) -> {
            boolean esEfectivo = val == rbEfectivo;
            boolean esCredito = val == rbCredito;
            lblMonto.setVisible(esEfectivo || esCredito);
            lblMonto.setManaged(esEfectivo || esCredito);
            txtMonto.setVisible(esEfectivo || esCredito);
            txtMonto.setManaged(esEfectivo || esCredito);
            lblCambio.setVisible(esEfectivo);
            lblCambio.setManaged(esEfectivo);
            lblCliente.setVisible(esCredito);
            lblCliente.setManaged(esCredito);
            txtCliente.setVisible(esCredito);
            txtCliente.setManaged(esCredito);
            if (esCredito) {
                lblMonto.setText("Anticipo (puede ser 0):");
                txtMonto.setPromptText("0.00");
            } else {
                lblMonto.setText("Monto recibido:");
            }
        });

        Label lblError = new Label();
        lblError.setStyle("-fx-text-fill: red;");

        Button btnConfirmar = new Button("✔ Confirmar Pago");
        btnConfirmar.setStyle("-fx-background-color: #28a745; -fx-text-fill: white; -fx-font-size: 14px; -fx-pref-height: 40px;");
        Button btnCancelar = new Button("Cancelar");

        btnConfirmar.setOnAction(e -> {
            boolean esCredito = rbCredito.isSelected();
            MetodoPago metodo = rbEfectivo.isSelected() ? MetodoPago.EFECTIVO
                    : rbTarjetaCredito.isSelected() ? MetodoPago.TARJETA_CREDITO
                    : esCredito ? MetodoPago.CREDITO
                    : MetodoPago.TARJETA_DEBITO;

            if (esCredito) {
                BigDecimal anticipo = BigDecimal.ZERO;
                String montoTxt = txtMonto.getText().trim();
                if (!montoTxt.isEmpty()) {
                    try { anticipo = new BigDecimal(montoTxt); }
                    catch (NumberFormatException ex) { lblError.setText("Anticipo inválido."); return; }
                }
                String cliente = txtCliente.getText().trim();
                try {
                    ventaActual.setTotal(totalACobrar[0]);
                    ventaActual = ventaService.completarVentaCredito(ventaActual.getId(), anticipo, cliente);
                    dialog.close();
                    mostrarAlerta("Venta a crédito registrada. Saldo pendiente: "
                            + CURRENCY.format(ventaActual.getSaldoPendiente()));
                    iniciarNuevaVenta();
                } catch (PosException ex) { lblError.setText(ex.getMessage()); }
                return;
            }

            BigDecimal monto = totalACobrar[0];
            if (metodo == MetodoPago.EFECTIVO) {
                try { monto = new BigDecimal(txtMonto.getText().trim()); }
                catch (NumberFormatException ex) { lblError.setText("Ingrese un monto válido."); return; }
            }

            try {
                ventaActual.setTotal(totalACobrar[0]);
                ventaActual = ventaService.completarVenta(ventaActual.getId(), metodo, monto);
                dialog.close();
                mostrarTicketEnPantalla(ventaActual);
                iniciarNuevaVenta();
            } catch (PagoInsuficienteException ex) {
                lblError.setText("Pago insuficiente. Faltan: " + CURRENCY.format(ex.getTotalVenta().subtract(ex.getMontoRecibido())));
            } catch (VentaVaciaException ex) {
                lblError.setText("La venta no tiene productos.");
            } catch (PosException ex) {
                lblError.setText(ex.getMessage());
            }
        });

        btnCancelar.setOnAction(e -> dialog.close());

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        int row = 0;
        grid.add(lblTotalOriginal, 0, row, 2, 1); row++;
        grid.add(new Label("Descuento:"), 0, row);
        grid.add(new HBox(8, rbDescMonto, rbDescPct, txtDescuento), 1, row); row++;
        grid.add(new Label(""), 0, row);
        grid.add(lblDescAplicado, 1, row); row++;
        grid.add(lblTotalFinal, 0, row, 2, 1); row++;
        grid.add(new Label("Método de pago:"), 0, row);
        grid.add(hbMetodo, 1, row); row++;
        grid.add(lblMonto, 0, row);
        grid.add(txtMonto, 1, row); row++;
        grid.add(lblCliente, 0, row);
        grid.add(txtCliente, 1, row); row++;
        grid.add(new Label(""), 0, row);
        grid.add(lblCambio, 1, row); row++;
        grid.add(lblError, 0, row, 2, 1); row++;
        grid.add(new HBox(12, btnConfirmar, btnCancelar), 0, row, 2, 1);

        dialog.setScene(new Scene(grid, 460, 400));
        dialog.showAndWait();
    }

    private void mostrarAlerta(String mensaje) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Información");
        a.setHeaderText(null);
        a.setContentText(mensaje);
        a.showAndWait();
    }

    // -------------------------------------------------------------------------
    // Ticket
    // -------------------------------------------------------------------------

    private void mostrarTicketEnPantalla(Venta venta) {
        StringBuilder sb = new StringBuilder();

        // Leer datos del negocio para la preview
        String negocio   = configuracion.get(ConfiguracionService.NEGOCIO_NOMBRE,   "Mi Negocio");
        String direccion = configuracion.get(ConfiguracionService.NEGOCIO_DIRECCION, "");
        String telefono  = configuracion.get(ConfiguracionService.NEGOCIO_TELEFONO,  "");
        String mensaje   = configuracion.get(ConfiguracionService.NEGOCIO_MENSAJE,   "¡Gracias por su compra!");

        if (!negocio.isBlank())   sb.append(centrar(negocio, 32)).append("\n");
        if (!direccion.isBlank()) sb.append(centrar(direccion, 32)).append("\n");
        if (!telefono.isBlank())  sb.append(centrar("Tel: " + telefono, 32)).append("\n");
        sb.append("--------------------------------\n");
        sb.append("Venta #").append(venta.getId()).append("\n");
        sb.append("Fecha:  ").append(venta.getFecha() != null
                ? venta.getFecha().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : "—").append("\n");
        sb.append("Cajero: ").append(venta.getCajero() != null
                ? venta.getCajero().getNombreCompleto() : "—").append("\n");
        sb.append("--------------------------------\n");
        for (LineaVenta l : venta.getLineas()) {
            sb.append(String.format("%-20s%n  %3d x %-8s = %s%n",
                    l.getNombreProducto(),
                    l.getCantidad(),
                    CURRENCY.format(l.getPrecioUnitario()),
                    CURRENCY.format(l.getSubtotal())));
        }
        sb.append("================================\n");
        sb.append(String.format("TOTAL:    %s%n", CURRENCY.format(venta.getTotal())));
        if (venta.getMetodoPago() == MetodoPago.EFECTIVO && venta.getCambio() != null) {
            sb.append(String.format("Recibido: %s%n", CURRENCY.format(venta.getMontoRecibido())));
            sb.append(String.format("Cambio:   %s%n", CURRENCY.format(venta.getCambio())));
        }
        sb.append("Método:   ").append(venta.getMetodoPago() != null ? venta.getMetodoPago().name() : "—").append("\n");
        sb.append("--------------------------------\n");
        if (!mensaje.isBlank()) sb.append(centrar(mensaje, 32)).append("\n");

        TextArea ta = new TextArea(sb.toString());
        ta.setEditable(false);
        ta.setStyle("-fx-font-family: monospace; -fx-font-size: 13px;");
        ta.setPrefSize(320, 420);

        // Botón imprimir solo si hay impresora configurada
        String impresoraConfig = configuracion.get(ConfiguracionService.PRINTER_NAME, "").trim();
        Button btnImprimir = new Button("🖨 Imprimir");
        btnImprimir.setStyle("-fx-background-color: #007bff; -fx-text-fill: white; -fx-font-size: 13px; -fx-pref-height: 36px;");
        btnImprimir.setDisable(impresoraConfig.isEmpty());
        if (impresoraConfig.isEmpty()) {
            btnImprimir.setText("🖨 Imprimir (sin impresora configurada)");
        }

        Button btnCerrar = new Button("Cerrar");
        btnCerrar.setStyle("-fx-pref-height: 36px;");

        javafx.scene.layout.HBox botones = new javafx.scene.layout.HBox(10, btnImprimir, btnCerrar);
        botones.setAlignment(javafx.geometry.Pos.CENTER);

        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Vista previa — Venta #" + venta.getId());

        btnImprimir.setOnAction(e -> {
            try {
                ticketPrinter.imprimirTicket(venta);
                log.info("Ticket impreso en '{}'", impresoraConfig);
                stage.close();
            } catch (Exception ex) {
                log.error("Error al imprimir ticket", ex);
                Alert err = new Alert(Alert.AlertType.ERROR);
                err.setTitle("Error de impresión");
                err.setHeaderText(null);
                err.setContentText("No se pudo imprimir: " + ex.getMessage());
                err.showAndWait();
            }
        });
        btnCerrar.setOnAction(e -> stage.close());

        VBox vbox = new VBox(12, ta, botones);
        vbox.setPadding(new Insets(16));
        vbox.setAlignment(Pos.CENTER);

        stage.setScene(new Scene(vbox, 380, 520));
        stage.show();
    }

    private String centrar(String texto, int ancho) {
        if (texto.length() >= ancho) return texto;
        int pad = (ancho - texto.length()) / 2;
        return " ".repeat(pad) + texto;
    }

    // -------------------------------------------------------------------------
    // Utilidades
    // -------------------------------------------------------------------------

    private void iniciarNuevaVenta() {
        if (usuarioActual == null) return;
        try {
            ventaActual = ventaService.iniciarVenta(usuarioActual.getId());
            lblVentaId.setText("Venta #" + ventaActual.getId());
            actualizarTabla();
            ocultarError();
        } catch (PosException e) {
            log.error("Error al iniciar venta", e);
            mostrarError("Error al iniciar venta: " + e.getMessage());
        }
    }

    private void actualizarTabla() {
        if (ventaActual == null) {
            tablaLineas.setItems(FXCollections.emptyObservableList());
            lblTotal.setText(CURRENCY.format(BigDecimal.ZERO));
            return;
        }
        tablaLineas.setItems(FXCollections.observableArrayList(ventaActual.getLineas()));
        lblTotal.setText(CURRENCY.format(ventaActual.getTotal()));
    }

    private void configurarTabla() {
        colProducto.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getNombreProducto()));
        colPrecio.setCellValueFactory(c ->
                new SimpleStringProperty(CURRENCY.format(c.getValue().getPrecioUnitario())));
        colCantidad.setCellValueFactory(c ->
                new SimpleStringProperty(String.valueOf(c.getValue().getCantidad())));
        colSubtotal.setCellValueFactory(c ->
                new SimpleStringProperty(CURRENCY.format(c.getValue().getSubtotal())));

        // Botón eliminar por fila
        colAccion.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("✕");
            {
                btn.setStyle("-fx-background-color: #dc3545; -fx-text-fill: white; -fx-background-radius: 4;");
                btn.setOnAction(e -> {
                    LineaVenta linea = getTableView().getItems().get(getIndex());
                    try {
                        ventaActual = ventaService.eliminarLinea(ventaActual.getId(), linea.getId());
                        actualizarTabla();
                    } catch (PosException ex) {
                        mostrarError(ex.getMessage());
                    }
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });
    }

    private void configurarAutoComplete() {
        txtBusqueda.textProperty().addListener((obs, oldVal, newVal) -> {
            // Si el usuario escribe manualmente, limpiar la selección previa
            if (!newVal.equals(oldVal)) {
                productoSeleccionadoId = null;
            }
            if (newVal == null || newVal.trim().length() < 1) {
                autoCompleteMenu.hide();
                return;
            }
            String termino = newVal.trim();
            log.info("[busqueda] Término: '{}'", termino);
            List<Producto> resultados = productoService.buscarPorNombreOCategoria(termino);
            log.info("[busqueda] Resultados ({}): {}", resultados.size(),
                    resultados.stream().map(p -> "id=" + p.getId() + " '" + p.getNombre() + "' stock=" + p.getStockActual()).toList());
            if (resultados.isEmpty()) {
                autoCompleteMenu.hide();
                return;
            }
            autoCompleteMenu.getItems().clear();
            resultados.stream().limit(10).forEach(p -> {
                String label = p.getNombre() + " — $" + p.getPrecioVenta()
                        + " (stock: " + p.getStockActual() + ")";
                MenuItem item = new MenuItem(label);
                item.setOnAction(e -> {
                    log.info("[busqueda] Seleccionado: id={} nombre='{}'", p.getId(), p.getNombre());
                    productoSeleccionadoId = p.getId();
                    txtBusqueda.setText(p.getNombre());
                    autoCompleteMenu.hide();
                    onAgregarProducto();
                });
                autoCompleteMenu.getItems().add(item);
            });
            autoCompleteMenu.show(txtBusqueda, javafx.geometry.Side.BOTTOM, 0, 0);
        });
    }

    private void mostrarError(String msg) {
        lblBusquedaError.setText(msg);
        lblBusquedaError.setVisible(true);
        lblBusquedaError.setManaged(true);
    }

    private void ocultarError() {
        lblBusquedaError.setVisible(false);
        lblBusquedaError.setManaged(false);
    }
}
