package com.pos.controller;

import com.pos.model.Usuario;
import com.pos.model.enums.Rol;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Controlador de la pantalla principal.
 * Gestiona la navegación entre módulos y el control de acceso por rol.
 */
public class MainController {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);
    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML private Label lblUsuario;
    @FXML private Label lblEstado;
    @FXML private Label lblFecha;
    @FXML private StackPane contentPane;

    // Botones del sidebar
    @FXML private Button btnVentas;
    @FXML private Button btnProductos;
    @FXML private Button btnInventario;
    @FXML private Button btnReportes;
    @FXML private Button btnImportar;
    @FXML private Button btnUsuarios;
    @FXML private Button btnHistorial;
    @FXML private Button btnConfiguracion;

    private Usuario usuarioActual;
    private Button btnActivo;

    @FXML
    public void initialize() {
        lblFecha.setText(LocalDateTime.now().format(FMT_FECHA));
    }

    /** Llamado desde LoginController después de autenticar. */
    public void setUsuarioActual(Usuario usuario) {
        this.usuarioActual = usuario;
        lblUsuario.setText(usuario.getNombreCompleto() + " (" + usuario.getRol().name() + ")");

        // Ocultar botón de usuarios si no es ADMIN
        boolean esAdmin = usuario.getRol() == Rol.ADMINISTRADOR;
        btnUsuarios.setVisible(esAdmin);
        btnUsuarios.setManaged(esAdmin);

        // Navegar a ventas por defecto
        onNavVentas();
    }

    // -------------------------------------------------------------------------
    // Navegación
    // -------------------------------------------------------------------------

    @FXML
    private void onNavVentas() {
        cargarVista("/fxml/venta.fxml", btnVentas, "Ventas");
    }

    @FXML
    private void onNavHistorial() {
        cargarVista("/fxml/historial.fxml", btnHistorial, "Historial de Ventas");
    }

    @FXML
    private void onNavProductos() {
        cargarVista("/fxml/producto.fxml", btnProductos, "Productos");
    }

    @FXML
    private void onNavInventario() {
        cargarVista("/fxml/inventario.fxml", btnInventario, "Inventario");
    }

    @FXML
    private void onNavReportes() {
        cargarVista("/fxml/reporte.fxml", btnReportes, "Reportes");
    }

    @FXML
    private void onNavImportar() {
        cargarVista("/fxml/importar.fxml", btnImportar, "Importar Excel");
    }

    @FXML
    private void onNavUsuarios() {
        if (usuarioActual == null || usuarioActual.getRol() != Rol.ADMINISTRADOR) {
            lblEstado.setText("Acceso denegado: se requiere rol ADMIN");
            return;
        }
        cargarVista("/fxml/usuario.fxml", btnUsuarios, "Gestión de Usuarios");
    }

    @FXML
    private void onNavConfiguracion() {
        cargarVista("/fxml/configuracion.fxml", btnConfiguracion, "Configuración");
    }

    @FXML
    private void onCerrarSesion() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) contentPane.getScene().getWindow();
            stage.setScene(new Scene(root, 640, 480));
            stage.setTitle("Sistema POS — Login");
            stage.centerOnScreen();
        } catch (IOException e) {
            log.error("Error al cerrar sesión", e);
        }
    }

    // -------------------------------------------------------------------------
    // Utilidades
    // -------------------------------------------------------------------------

    private void cargarVista(String fxmlPath, Button boton, String titulo) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Node vista = loader.load();

            // Pasar usuario al controlador si lo soporta
            Object ctrl = loader.getController();
            if (ctrl instanceof UsuarioAwareController uac) {
                uac.setUsuarioActual(usuarioActual);
            }

            contentPane.getChildren().setAll(vista);
            lblEstado.setText(titulo);
            lblFecha.setText(LocalDateTime.now().format(FMT_FECHA));

            // Actualizar estilo del botón activo
            if (btnActivo != null) {
                btnActivo.getStyleClass().remove("sidebar-btn-active");
            }
            if (boton != null) {
                boton.getStyleClass().add("sidebar-btn-active");
                btnActivo = boton;
            }
        } catch (Exception e) {
            log.error("Error al cargar vista {}", fxmlPath, e);
            // Construir mensaje con toda la cadena de causas + stack trace
            StringBuilder msg = new StringBuilder();
            msg.append("Error al cargar ").append(titulo).append(":\n\n");
            Throwable t = e;
            int depth = 0;
            while (t != null && depth < 10) {
                msg.append("  [").append(depth).append("] ")
                   .append(t.getClass().getName()).append(": ")
                   .append(t.getMessage() != null ? t.getMessage() : "(mensaje nulo)").append("\n");
                // Mostrar primeras líneas del stack trace de cada causa
                StackTraceElement[] frames = t.getStackTrace();
                for (int i = 0; i < Math.min(5, frames.length); i++) {
                    msg.append("      at ").append(frames[i]).append("\n");
                }
                t = t.getCause();
                depth++;
            }
            e.printStackTrace();
            lblEstado.setText("Error al cargar " + titulo);
            javafx.scene.control.TextArea ta = new javafx.scene.control.TextArea(msg.toString());
            ta.setEditable(false);
            ta.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");
            contentPane.getChildren().setAll(ta);
        }
    }

    /** Interfaz para controladores que necesitan saber el usuario actual. */
    public interface UsuarioAwareController {
        void setUsuarioActual(Usuario usuario);
    }
}
