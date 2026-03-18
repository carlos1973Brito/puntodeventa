package com.pos.controller;

import com.pos.config.DatabaseConfig;
import com.pos.exception.PosException;
import com.pos.model.Venta;
import com.pos.model.Usuario;
import com.pos.model.enums.EstadoVenta;
import com.pos.model.enums.MetodoPago;
import com.pos.repository.impl.MovimientoStockRepositoryImpl;
import com.pos.repository.impl.ProductoRepositoryImpl;
import com.pos.repository.impl.UsuarioRepositoryImpl;
import com.pos.repository.impl.VentaRepositoryImpl;
import com.pos.service.ConfiguracionService;
import com.pos.service.TicketPrinterService;
import com.pos.service.VentaService;
import com.pos.service.impl.TicketPrinterServiceImpl;
import com.pos.service.impl.VentaServiceImpl;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class HistorialController implements MainController.UsuarioAwareController {

    private static final Logger log = LoggerFactory.getLogger(HistorialController.class);
    private static final    DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FMT_DIA = DateTimeFormatter.ofPattern("dd/MM");
    private static final NumberFormat CURRENCY = NumberFormat.getCurrencyInstance(new Locale("es", "MX"));

    // Tab Ventas
    @FXML private DatePicker dpDesde;
    @FXML private DatePicker dpHasta;
    @FXML private TableView<Venta> tablaVentas;
    @FXML private TableColumn<Venta, String> colId;
    @FXML private TableColumn<Venta, String> colFecha;
    @FXML private TableColumn<Venta, String> colCajero;
    @FXML private TableColumn<Venta, String> colCliente;
    @FXML private TableColumn<Venta, String> colTotal;
    @FXML private TableColumn<Venta, String> colMetodo;
    @FXML private TableColumn<Venta, String> colEstado;
    @FXML private TableColumn<Venta, String> colSaldo;

    // Tab Créditos
    @FXML private TableView<Venta> tablaCreditos;
    @FXML private TableColumn<Venta, String> colCrId;
    @FXML private TableColumn<Venta, String> colCrFecha;
    @FXML private TableColumn<Venta, String> colCrCliente;
    @FXML private TableColumn<Venta, String> colCrTotal;
    @FXML private TableColumn<Venta, String> colCrPagado;
    @FXML private TableColumn<Venta, String> colCrSaldo;

    // Tab Corte
    @FXML private DatePicker dpCorte;
    @FXML private TextArea txtCorte;
    @FXML private BarChart<String, Number> graficaVentas;
    @FXML private javafx.scene.chart.CategoryAxis ejeX;
    @FXML private javafx.scene.chart.NumberAxis ejeY;

    private VentaService ventaService;
    private VentaRepositoryImpl ventaRepository;
    private final TicketPrinterService ticketPrinter = new TicketPrinterServiceImpl();
    private final ConfiguracionService configuracion = new ConfiguracionService();

    @FXML
    public void initialize() {
        try {
            SessionFactory sf = DatabaseConfig.buildSessionFactory();
            ventaRepository = new VentaRepositoryImpl(sf);
            ventaService = new VentaServiceImpl(
                    ventaRepository,
                    new ProductoRepositoryImpl(sf),
                    new MovimientoStockRepositoryImpl(sf),
                    new UsuarioRepositoryImpl(sf)
            );

            dpDesde.setValue(LocalDate.now().withDayOfMonth(1));
            dpHasta.setValue(LocalDate.now());
            dpCorte.setValue(LocalDate.now());

            configurarTablaVentas();
            configurarTablaCreditos();
            onActualizarCreditos();
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(HistorialController.class)
                    .error("Error en HistorialController.initialize()", e);
            throw e;
        }
    }

    @Override
    public void setUsuarioActual(Usuario usuario) {
        // no se requiere en esta vista
    }

    // -------------------------------------------------------------------------
    // Acciones - Tab Ventas
    // -------------------------------------------------------------------------

    @FXML
    private void onBuscar() {
        LocalDate desde = dpDesde.getValue();
        LocalDate hasta = dpHasta.getValue();
        if (desde == null || hasta == null) return;

        List<Venta> ventas = ventaRepository.findByFechaBetween(
                desde.atStartOfDay(), hasta.plusDays(1).atStartOfDay());
        tablaVentas.setItems(FXCollections.observableArrayList(ventas));
    }

    @FXML
    private void onReimprimir() {
        Venta seleccionada = tablaVentas.getSelectionModel().getSelectedItem();
        if (seleccionada == null) {
            mostrarAlerta(Alert.AlertType.WARNING, "Sin selección", "Selecciona una venta para reimprimir.");
            return;
        }
        String impresoraConfig = configuracion.get(ConfiguracionService.PRINTER_NAME, "").trim();
        if (!impresoraConfig.isEmpty()) {
            try {
                ticketPrinter.imprimirTicket(seleccionada);
                mostrarAlerta(Alert.AlertType.INFORMATION, "Impreso",
                        "Ticket de venta #" + seleccionada.getId() + " enviado a la impresora.");
                return;
            } catch (Exception e) {
                log.warn("No se pudo imprimir en térmica: {}", e.getMessage());
            }
        }
        mostrarTicketEnPantalla(seleccionada);
    }

    private void mostrarTicketEnPantalla(Venta venta) {
        StringBuilder sb = new StringBuilder();
        sb.append("===== TICKET DE VENTA =====\n");
        sb.append("Venta #").append(venta.getId()).append("\n");
        sb.append("Fecha: ").append(venta.getFecha() != null ? venta.getFecha().format(FMT) : "—").append("\n");
        sb.append("Cajero: ").append(venta.getCajero() != null ? venta.getCajero().getNombreCompleto() : "—").append("\n");
        sb.append("---------------------------\n");
        if (venta.getLineas() != null) {
            for (var l : venta.getLineas()) {
                sb.append(String.format("%-20s %3d x %s = %s%n",
                        l.getNombreProducto(), l.getCantidad(),
                        CURRENCY.format(l.getPrecioUnitario()),
                        CURRENCY.format(l.getSubtotal())));
            }
        }
        sb.append("---------------------------\n");
        sb.append("TOTAL: ").append(CURRENCY.format(venta.getTotal())).append("\n");
        if (venta.getMetodoPago() == MetodoPago.EFECTIVO && venta.getCambio() != null) {
            sb.append("Recibido: ").append(CURRENCY.format(venta.getMontoRecibido())).append("\n");
            sb.append("Cambio:   ").append(CURRENCY.format(venta.getCambio())).append("\n");
        }
        sb.append("Método: ").append(venta.getMetodoPago() != null ? venta.getMetodoPago().name() : "—").append("\n");
        sb.append("===========================\n");

        TextArea ta = new TextArea(sb.toString());
        ta.setEditable(false);
        ta.setStyle("-fx-font-family: monospace; -fx-font-size: 13px;");
        ta.setPrefSize(360, 320);

        Button btnCerrar = new Button("Cerrar");
        VBox vbox = new VBox(12, ta, btnCerrar);
        vbox.setPadding(new Insets(16));
        vbox.setAlignment(Pos.CENTER);

        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Ticket — Venta #" + venta.getId());
        stage.setScene(new Scene(vbox, 400, 400));
        btnCerrar.setOnAction(e -> stage.close());
        stage.show();
    }

    @FXML
    private void onDevolver() {        Venta seleccionada = tablaVentas.getSelectionModel().getSelectedItem();
        if (seleccionada == null) {
            mostrarAlerta(Alert.AlertType.WARNING, "Sin selección", "Selecciona una venta para devolver.");
            return;
        }
        if (seleccionada.getEstado() == EstadoVenta.DEVUELTA) {
            mostrarAlerta(Alert.AlertType.WARNING, "Ya devuelta", "Esta venta ya fue devuelta.");
            return;
        }
        if (seleccionada.getEstado() == EstadoVenta.EN_CURSO || seleccionada.getEstado() == EstadoVenta.CANCELADA) {
            mostrarAlerta(Alert.AlertType.WARNING, "No aplica", "Solo se pueden devolver ventas completadas o a crédito.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Devolución");
        dialog.setHeaderText("Venta #" + seleccionada.getId() + " — Total: " + CURRENCY.format(seleccionada.getTotal()));
        dialog.setContentText("Motivo de devolución:");
        dialog.showAndWait().ifPresent(motivo -> {
            if (motivo.trim().isEmpty()) {
                mostrarAlerta(Alert.AlertType.WARNING, "Motivo requerido", "Ingresa el motivo de la devolución.");
                return;
            }
            try {
                ventaService.devolverVenta(seleccionada.getId(), motivo.trim());
                mostrarAlerta(Alert.AlertType.INFORMATION, "Devolución exitosa",
                        "Venta #" + seleccionada.getId() + " devuelta. Stock repuesto.");
                onBuscar();
            } catch (PosException e) {
                mostrarAlerta(Alert.AlertType.ERROR, "Error", e.getMessage());
            }
        });
    }

    // -------------------------------------------------------------------------
    // Acciones - Tab Créditos
    // -------------------------------------------------------------------------

    @FXML
    private void onActualizarCreditos() {
        List<Venta> creditos = ventaService.listarCreditos();
        tablaCreditos.setItems(FXCollections.observableArrayList(creditos));
    }

    @FXML
    private void onAbonar() {
        Venta seleccionada = tablaCreditos.getSelectionModel().getSelectedItem();
        if (seleccionada == null) {
            mostrarAlerta(Alert.AlertType.WARNING, "Sin selección", "Selecciona un crédito para abonar.");
            return;
        }
        mostrarDialogoAbono(seleccionada);
    }

    private void mostrarDialogoAbono(Venta venta) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Registrar Abono");

        Label lblInfo = new Label("Cliente: " + nvl(venta.getNombreCliente()));
        Label lblSaldo = new Label("Saldo pendiente: " + CURRENCY.format(venta.getSaldoPendiente()));
        lblSaldo.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #dc3545;");

        TextField txtMonto = new TextField();
        txtMonto.setPromptText("Monto del abono");

        Label lblError = new Label();
        lblError.setStyle("-fx-text-fill: red;");

        Button btnConfirmar = new Button("✔ Registrar Abono");
        btnConfirmar.setStyle("-fx-background-color: #28a745; -fx-text-fill: white;");
        Button btnCancelar = new Button("Cancelar");

        btnConfirmar.setOnAction(e -> {
            try {
                BigDecimal monto = new BigDecimal(txtMonto.getText().trim());
                ventaService.abonarCredito(venta.getId(), monto);
                dialog.close();
                mostrarAlerta(Alert.AlertType.INFORMATION, "Abono registrado",
                        "Abono de " + CURRENCY.format(monto) + " registrado.");
                onActualizarCreditos();
            } catch (NumberFormatException ex) {
                lblError.setText("Ingresa un monto válido.");
            } catch (PosException ex) {
                lblError.setText(ex.getMessage());
            }
        });
        btnCancelar.setOnAction(e -> dialog.close());

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.add(lblInfo, 0, 0, 2, 1);
        grid.add(lblSaldo, 0, 1, 2, 1);
        grid.add(new Label("Monto:"), 0, 2);
        grid.add(txtMonto, 1, 2);
        grid.add(lblError, 0, 3, 2, 1);
        grid.add(new javafx.scene.layout.HBox(10, btnConfirmar, btnCancelar), 0, 4, 2, 1);

        dialog.setScene(new Scene(grid, 360, 220));
        dialog.showAndWait();
    }

    // -------------------------------------------------------------------------
    // Acciones - Tab Corte de Caja
    // -------------------------------------------------------------------------

    @FXML
    private void onGenerarCorte() {
        LocalDate fecha = dpCorte.getValue();
        if (fecha == null) return;

        List<Venta> ventas = ventaRepository.findByFechaBetween(
                fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay());

        List<Venta> completadas = ventas.stream()
                .filter(v -> v.getEstado() == EstadoVenta.COMPLETADA || v.getEstado() == EstadoVenta.CREDITO)
                .toList();

        BigDecimal totalDia = completadas.stream().map(Venta::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalEfectivo = completadas.stream()
                .filter(v -> v.getMetodoPago() == MetodoPago.EFECTIVO)
                .map(Venta::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalTarjeta = completadas.stream()
                .filter(v -> v.getMetodoPago() == MetodoPago.TARJETA_CREDITO || v.getMetodoPago() == MetodoPago.TARJETA_DEBITO)
                .map(Venta::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredito = completadas.stream()
                .filter(v -> v.getMetodoPago() == MetodoPago.CREDITO)
                .map(Venta::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);

        // Agrupar por cajero
        Map<String, BigDecimal> porCajero = new LinkedHashMap<>();
        for (Venta v : completadas) {
            String cajero = v.getCajero() != null ? v.getCajero().getNombreCompleto() : "Sistema";
            porCajero.merge(cajero, v.getTotal(), BigDecimal::add);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("========== CORTE DE CAJA ==========\n");
        sb.append("Fecha: ").append(fecha.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))).append("\n\n");
        sb.append("Total ventas:    ").append(completadas.size()).append("\n");
        sb.append("Total del día:   ").append(CURRENCY.format(totalDia)).append("\n\n");
        sb.append("--- Por método de pago ---\n");
        sb.append(String.format("  Efectivo:      %s%n", CURRENCY.format(totalEfectivo)));
        sb.append(String.format("  Tarjeta:       %s%n", CURRENCY.format(totalTarjeta)));
        sb.append(String.format("  Crédito:       %s%n", CURRENCY.format(totalCredito)));
        sb.append("\n--- Por cajero ---\n");
        porCajero.forEach((cajero, total) ->
                sb.append(String.format("  %-20s %s%n", cajero, CURRENCY.format(total))));
        sb.append("===================================\n");
        txtCorte.setText(sb.toString());

        // Gráfica: ventas de los últimos 7 días
        generarGrafica(fecha);
    }

    private void generarGrafica(LocalDate hasta) {
        LocalDate desde = hasta.minusDays(6);
        List<Venta> ventas = ventaRepository.findByFechaBetween(
                desde.atStartOfDay(), hasta.plusDays(1).atStartOfDay());

        // Agrupar por día
        Map<LocalDate, BigDecimal> porDia = new TreeMap<>();
        for (LocalDate d = desde; !d.isAfter(hasta); d = d.plusDays(1)) {
            porDia.put(d, BigDecimal.ZERO);
        }
        for (Venta v : ventas) {
            if (v.getEstado() == EstadoVenta.COMPLETADA || v.getEstado() == EstadoVenta.CREDITO) {
                LocalDate dia = v.getFecha().toLocalDate();
                porDia.merge(dia, v.getTotal(), BigDecimal::add);
            }
        }

        XYChart.Series<String, Number> serie = new XYChart.Series<>();
        serie.setName("Ventas");
        porDia.forEach((dia, total) ->
                serie.getData().add(new XYChart.Data<>(dia.format(FMT_DIA), total.doubleValue())));

        graficaVentas.getData().clear();
        graficaVentas.getData().add(serie);
        graficaVentas.setTitle("Ventas últimos 7 días");
    }

    // -------------------------------------------------------------------------
    // Configuración de tablas
    // -------------------------------------------------------------------------

    private void configurarTablaVentas() {
        colId.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getId())));
        colFecha.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getFecha() != null ? c.getValue().getFecha().format(FMT) : "—"));
        colCajero.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getCajero() != null ? c.getValue().getCajero().getNombreCompleto() : "—"));
        colCliente.setCellValueFactory(c -> new SimpleStringProperty(nvl(c.getValue().getNombreCliente())));
        colTotal.setCellValueFactory(c -> new SimpleStringProperty(CURRENCY.format(c.getValue().getTotal())));
        colMetodo.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getMetodoPago() != null ? c.getValue().getMetodoPago().name() : "—"));
        colEstado.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEstado().name()));
        colSaldo.setCellValueFactory(c -> {
            BigDecimal saldo = c.getValue().getSaldoPendiente();
            return new SimpleStringProperty(saldo != null && saldo.compareTo(BigDecimal.ZERO) > 0
                    ? CURRENCY.format(saldo) : "—");
        });

        // Colorear filas por estado
        tablaVentas.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(Venta v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setStyle(""); return; }
                switch (v.getEstado()) {
                    case DEVUELTA -> setStyle("-fx-background-color: #f8d7da;");
                    case CREDITO  -> setStyle("-fx-background-color: #fff3cd;");
                    default       -> setStyle("");
                }
            }
        });
    }

    private void configurarTablaCreditos() {
        colCrId.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getId())));
        colCrFecha.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getFecha() != null ? c.getValue().getFecha().format(FMT) : "—"));
        colCrCliente.setCellValueFactory(c -> new SimpleStringProperty(nvl(c.getValue().getNombreCliente())));
        colCrTotal.setCellValueFactory(c -> new SimpleStringProperty(CURRENCY.format(c.getValue().getTotal())));
        colCrPagado.setCellValueFactory(c -> new SimpleStringProperty(
                CURRENCY.format(c.getValue().getMontoRecibido() != null ? c.getValue().getMontoRecibido() : BigDecimal.ZERO)));
        colCrSaldo.setCellValueFactory(c -> new SimpleStringProperty(CURRENCY.format(c.getValue().getSaldoPendiente())));
    }

    // -------------------------------------------------------------------------
    // Utilidades
    // -------------------------------------------------------------------------

    private void mostrarAlerta(Alert.AlertType tipo, String titulo, String msg) {
        Alert a = new Alert(tipo);
        a.setTitle(titulo);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    private String nvl(String s) { return s != null && !s.isBlank() ? s : "—"; }
}
