package com.pos.controller;

import com.pos.service.ConfiguracionService;
import com.pos.service.TicketPrinterService;
import com.pos.service.impl.TicketPrinterServiceImpl;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Controlador de la pantalla de Configuración (impresora y datos del negocio).
 */
public class ConfiguracionController implements MainController.UsuarioAwareController {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracionController.class);

    // ── Impresora ──
    @FXML private ComboBox<String> cmbImpresora;
    @FXML private ComboBox<String> cmbAncho;
    @FXML private Button btnPrueba;

    // ── Negocio ──
    @FXML private TextField txtNombre;
    @FXML private TextField txtDireccion;
    @FXML private TextField txtTelefono;
    @FXML private TextField txtMensaje;

    @FXML private Label lblEstado;

    private final ConfiguracionService config = new ConfiguracionService();
    private final TicketPrinterService printer = new TicketPrinterServiceImpl();

    @FXML
    public void initialize() {
        // Cargar impresoras disponibles
        List<String> impresoras = printer.listarImpresoras();
        cmbImpresora.setItems(FXCollections.observableArrayList(impresoras));

        // Anchos de papel
        cmbAncho.setItems(FXCollections.observableArrayList("32 chars (58mm)", "48 chars (80mm)"));

        // Cargar valores guardados
        String impresoraGuardada = config.get(ConfiguracionService.PRINTER_NAME, "");
        if (!impresoraGuardada.isEmpty() && impresoras.contains(impresoraGuardada)) {
            cmbImpresora.setValue(impresoraGuardada);
        }

        String ancho = config.get(ConfiguracionService.PRINTER_WIDTH, "32");
        cmbAncho.setValue(ancho.equals("48") ? "48 chars (80mm)" : "32 chars (58mm)");

        txtNombre.setText(config.get(ConfiguracionService.NEGOCIO_NOMBRE, ""));
        txtDireccion.setText(config.get(ConfiguracionService.NEGOCIO_DIRECCION, ""));
        txtTelefono.setText(config.get(ConfiguracionService.NEGOCIO_TELEFONO, ""));
        txtMensaje.setText(config.get(ConfiguracionService.NEGOCIO_MENSAJE, "¡Gracias por su compra!"));
    }

    @Override
    public void setUsuarioActual(com.pos.model.Usuario usuario) {
        // no se necesita el usuario aquí
    }

    @FXML
    private void onGuardar() {
        String impresora = cmbImpresora.getValue();
        if (impresora == null || impresora.isBlank()) {
            mostrarEstado("⚠ Selecciona una impresora.", false);
            return;
        }

        String anchoStr = cmbAncho.getValue();
        String ancho = (anchoStr != null && anchoStr.startsWith("48")) ? "48" : "32";

        config.set(ConfiguracionService.PRINTER_NAME,     impresora);
        config.set(ConfiguracionService.PRINTER_WIDTH,    ancho);
        config.set(ConfiguracionService.NEGOCIO_NOMBRE,   txtNombre.getText().trim());
        config.set(ConfiguracionService.NEGOCIO_DIRECCION, txtDireccion.getText().trim());
        config.set(ConfiguracionService.NEGOCIO_TELEFONO,  txtTelefono.getText().trim());
        config.set(ConfiguracionService.NEGOCIO_MENSAJE,   txtMensaje.getText().trim());

        config.guardar();
        mostrarEstado("✔ Configuración guardada.", true);
    }

    @FXML
    private void onPrueba() {
        String impresora = cmbImpresora.getValue();
        if (impresora == null || impresora.isBlank()) {
            mostrarEstado("⚠ Selecciona una impresora primero.", false);
            return;
        }
        try {
            printer.imprimirPrueba(impresora);
            mostrarEstado("✔ Prueba enviada a '" + impresora + "'.", true);
        } catch (Exception e) {
            log.error("Error en prueba de impresión", e);
            mostrarEstado("✗ Error: " + e.getMessage(), false);
        }
    }

    @FXML
    private void onRefrescarImpresoras() {
        List<String> impresoras = printer.listarImpresoras();
        cmbImpresora.setItems(FXCollections.observableArrayList(impresoras));
        mostrarEstado("Impresoras actualizadas (" + impresoras.size() + " encontradas).", true);
    }

    private void mostrarEstado(String msg, boolean ok) {
        lblEstado.setText(msg);
        lblEstado.setStyle(ok
                ? "-fx-text-fill: #28a745; -fx-font-weight: bold;"
                : "-fx-text-fill: #dc3545; -fx-font-weight: bold;");
    }
}
