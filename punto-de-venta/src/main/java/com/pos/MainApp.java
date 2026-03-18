package com.pos;

import com.pos.config.DatabaseConfig;
import com.pos.dto.UsuarioDTO;
import com.pos.model.enums.Rol;
import com.pos.repository.impl.UsuarioRepositoryImpl;
import com.pos.service.impl.UsuarioServiceImpl;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Punto de entrada principal de la aplicación POS.
 * Inicializa Hibernate y carga la pantalla de login.
 */
public class MainApp extends Application {

    private static final Logger logger = LoggerFactory.getLogger(MainApp.class);

    @Override
    public void start(Stage primaryStage) {
        try {
            // Inicializar base de datos
            DatabaseConfig.buildSessionFactory();
            logger.info("Base de datos inicializada. Tipo: {}", DatabaseConfig.getDatabaseType());

            // Crear usuario admin por defecto si no existe ninguno
            crearAdminSiNoExiste();

            // Cargar pantalla de login
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root, 640, 480);
            primaryStage.setTitle("Sistema POS — Login");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(480);
            primaryStage.setMinHeight(400);
            primaryStage.centerOnScreen();
            primaryStage.show();

        } catch (Exception e) {
            logger.error("Error al iniciar la aplicación: {}", e.getMessage(), e);
            showErrorAndExit(primaryStage, e.getMessage());
        }
    }

    private void crearAdminSiNoExiste() {
        try {
            var repo = new UsuarioRepositoryImpl(DatabaseConfig.buildSessionFactory());
            if (repo.findByUsername("admin").isEmpty()) {
                var service = new UsuarioServiceImpl(repo);
                service.crear(new UsuarioDTO("admin", "admin1234", "Administrador", Rol.ADMINISTRADOR));
                logger.info("Usuario admin creado con contraseña por defecto: admin1234");
            }
        } catch (Exception e) {
            logger.warn("No se pudo crear el usuario admin inicial: {}", e.getMessage());
        }
    }

    @Override
    public void stop() {
        logger.info("Cerrando aplicación...");
        DatabaseConfig.shutdown();
    }

    private void showErrorAndExit(Stage stage, String message) {
        Label errorLabel = new Label("Error al iniciar: " + message);
        errorLabel.setStyle("-fx-text-fill: red; -fx-font-size: 14px;");
        VBox root = new VBox(errorLabel);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(20));

        stage.setTitle("Error de inicio");
        stage.setScene(new Scene(root, 500, 150));
        stage.show();
        stage.setOnCloseRequest(e -> Platform.exit());
    }

    public static void main(String[] args) {
        launch(args);
    }
}
