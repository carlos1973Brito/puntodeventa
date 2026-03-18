package com.pos.controller;

import com.pos.config.DatabaseConfig;
import com.pos.exception.CredencialesInvalidasException;
import com.pos.exception.UsuarioBloqueadoException;
import com.pos.model.Usuario;
import com.pos.repository.impl.UsuarioRepositoryImpl;
import com.pos.service.UsuarioService;
import com.pos.service.impl.UsuarioServiceImpl;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.format.DateTimeFormatter;

/**
 * Controlador de la pantalla de login.
 * Maneja autenticación, bloqueo de cuenta y navegación a la pantalla principal.
 */
public class LoginController {

    private static final Logger log = LoggerFactory.getLogger(LoginController.class);

    @FXML private TextField txtUsuario;
    @FXML private PasswordField txtPassword;
    @FXML private Button btnLogin;
    @FXML private Label lblError;
    @FXML private Label lblVersion;

    private UsuarioService usuarioService;

    @FXML
    public void initialize() {
        usuarioService = new UsuarioServiceImpl(
                new UsuarioRepositoryImpl(DatabaseConfig.buildSessionFactory())
        );
        lblVersion.setText("v1.0");
        // Enfocar el campo de usuario al abrir
        Platform.runLater(() -> txtUsuario.requestFocus());
    }

    @FXML
    private void onLogin() {
        String username = txtUsuario.getText().trim();
        String password = txtPassword.getText();

        if (username.isEmpty() || password.isEmpty()) {
            mostrarError("Por favor ingrese usuario y contraseña.");
            return;
        }

        ocultarError();
        btnLogin.setDisable(true);

        try {
            Usuario usuario = usuarioService.autenticar(username, password)
                    .orElseThrow(com.pos.exception.CredencialesInvalidasException::new);
            abrirPantallaPrincipal(usuario);
        } catch (UsuarioBloqueadoException e) {
            String hasta = e.getBloqueadoHasta() != null
                    ? e.getBloqueadoHasta().format(DateTimeFormatter.ofPattern("HH:mm"))
                    : "unos minutos";
            mostrarError("Cuenta bloqueada hasta las " + hasta + " por múltiples intentos fallidos.");
        } catch (CredencialesInvalidasException e) {
            mostrarError("Usuario o contraseña incorrectos.");
            txtPassword.clear();
            txtPassword.requestFocus();
        } catch (Exception e) {
            log.error("Error inesperado en login", e);
            mostrarError("Error al iniciar sesión: " + e.getMessage());
        } finally {
            btnLogin.setDisable(false);
        }
    }

    private void abrirPantallaPrincipal(Usuario usuario) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/main.fxml"));
            Parent root = loader.load();

            MainController mainController = loader.getController();
            mainController.setUsuarioActual(usuario);

            Stage stage = (Stage) btnLogin.getScene().getWindow();
            Scene scene = new Scene(root, 1100, 700);
            stage.setScene(scene);
            stage.setTitle("Sistema POS — " + usuario.getNombreCompleto());
            stage.setMinWidth(900);
            stage.setMinHeight(600);
            stage.centerOnScreen();
        } catch (IOException e) {
            log.error("Error al cargar pantalla principal", e);
            mostrarError("Error al cargar la aplicación: " + e.getMessage());
        }
    }

    private void mostrarError(String mensaje) {
        lblError.setText(mensaje);
        lblError.setVisible(true);
        lblError.setManaged(true);
    }

    private void ocultarError() {
        lblError.setVisible(false);
        lblError.setManaged(false);
    }
}
