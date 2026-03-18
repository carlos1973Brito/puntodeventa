package com.pos.controller;

import com.pos.config.DatabaseConfig;
import com.pos.dto.UsuarioDTO;
import com.pos.model.Usuario;
import com.pos.model.enums.Rol;
import com.pos.repository.impl.UsuarioRepositoryImpl;
import com.pos.service.UsuarioService;
import com.pos.service.impl.UsuarioServiceImpl;
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

import java.util.List;

/**
 * Controlador de la vista de Gestión de Usuarios.
 * Solo accesible para el rol ADMINISTRADOR.
 * Permite listar, crear, editar, desactivar y restablecer contraseña de usuarios.
 */
public class UsuarioController implements MainController.UsuarioAwareController {

    private static final Logger log = LoggerFactory.getLogger(UsuarioController.class);

    @FXML private Label lblInfo;
    @FXML private TableView<Usuario> tablaUsuarios;
    @FXML private TableColumn<Usuario, String> colUsername;
    @FXML private TableColumn<Usuario, String> colNombre;
    @FXML private TableColumn<Usuario, String> colRol;
    @FXML private TableColumn<Usuario, String> colActivo;
    @FXML private TableColumn<Usuario, String> colDebeCambiar;
    @FXML private TableColumn<Usuario, Void>   colAcciones;

    private UsuarioService usuarioService;
    private UsuarioRepositoryImpl usuarioRepository;
    private Usuario usuarioActual;

    @FXML
    public void initialize() {
        SessionFactory sf = DatabaseConfig.buildSessionFactory();
        usuarioRepository = new UsuarioRepositoryImpl(sf);
        usuarioService = new UsuarioServiceImpl(usuarioRepository);
        configurarTabla();
        cargarUsuarios();
    }

    @Override
    public void setUsuarioActual(Usuario usuario) {
        this.usuarioActual = usuario;
    }

    // -------------------------------------------------------------------------
    // Acciones
    // -------------------------------------------------------------------------

    @FXML
    private void onNuevo() {
        mostrarFormulario(null);
    }

    // -------------------------------------------------------------------------
    // Formulario crear / editar
    // -------------------------------------------------------------------------

    private void mostrarFormulario(Usuario usuarioEditar) {
        boolean esNuevo = usuarioEditar == null;
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(esNuevo ? "Nuevo Usuario" : "Editar Usuario");

        TextField txtUsername      = new TextField(esNuevo ? "" : usuarioEditar.getUsername());
        TextField txtNombreCompleto = new TextField(esNuevo ? "" : usuarioEditar.getNombreCompleto());
        PasswordField txtPassword  = new PasswordField();
        PasswordField txtConfirm   = new PasswordField();
        ComboBox<Rol> cmbRol       = new ComboBox<>(FXCollections.observableArrayList(Rol.values()));
        cmbRol.setValue(esNuevo ? Rol.CAJERO : usuarioEditar.getRol());

        Label lblError = new Label();
        lblError.setStyle("-fx-text-fill: red; -fx-font-size: 12px;");
        lblError.setWrapText(true);

        // En edición el username no se puede cambiar
        if (!esNuevo) {
            txtUsername.setDisable(true);
        }

        Button btnGuardar  = new Button(esNuevo ? "Crear" : "Guardar");
        btnGuardar.setStyle("-fx-background-color: #4a90d9; -fx-text-fill: white;");
        Button btnCancelar = new Button("Cancelar");

        btnGuardar.setOnAction(e -> {
            String username       = txtUsername.getText().trim();
            String nombreCompleto = txtNombreCompleto.getText().trim();
            String password       = txtPassword.getText();
            String confirm        = txtConfirm.getText();
            Rol rol               = cmbRol.getValue();

            // Validaciones básicas
            if (username.isEmpty() || nombreCompleto.isEmpty()) {
                lblError.setText("Usuario y nombre completo son obligatorios.");
                return;
            }
            if (esNuevo && password.isEmpty()) {
                lblError.setText("La contraseña es obligatoria para nuevos usuarios.");
                return;
            }
            if (!password.isEmpty() && password.length() < 8) {
                lblError.setText("La contraseña debe tener al menos 8 caracteres.");
                return;
            }
            if (!password.isEmpty() && !password.equals(confirm)) {
                lblError.setText("Las contraseñas no coinciden.");
                return;
            }
            if (rol == null) {
                lblError.setText("Seleccione un rol.");
                return;
            }

            try {
                if (esNuevo) {
                    UsuarioDTO dto = new UsuarioDTO(username, password, nombreCompleto, rol);
                    usuarioService.crear(dto);
                } else {
                    // Actualizar nombre y rol directamente en la entidad
                    usuarioEditar.setNombreCompleto(nombreCompleto);
                    usuarioEditar.setRol(rol);
                    // Si se proporcionó nueva contraseña, cambiarla
                    if (!password.isEmpty()) {
                        usuarioService.cambiarContrasena(usuarioEditar.getId(), password);
                    }
                    // Guardar cambios de nombre/rol a través del repositorio vía service
                    // Usamos un DTO temporal para reutilizar la lógica de guardado
                    guardarCambiosUsuario(usuarioEditar);
                }
                dialog.close();
                cargarUsuarios();
                mostrarInfo("Usuario " + (esNuevo ? "creado" : "actualizado") + " correctamente.");
            } catch (Exception ex) {
                log.error("Error al guardar usuario", ex);
                lblError.setText("Error: " + ex.getMessage());
            }
        });

        btnCancelar.setOnAction(e -> dialog.close());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        int row = 0;
        grid.add(new Label("Usuario *:"),          0, row); grid.add(txtUsername,       1, row++);
        grid.add(new Label("Nombre completo *:"),  0, row); grid.add(txtNombreCompleto, 1, row++);
        grid.add(new Label("Rol *:"),              0, row); grid.add(cmbRol,            1, row++);
        grid.add(new Label(esNuevo ? "Contraseña *:" : "Nueva contraseña:"),
                                                   0, row); grid.add(txtPassword,       1, row++);
        grid.add(new Label("Confirmar contraseña:"),0, row); grid.add(txtConfirm,       1, row++);
        grid.add(lblError,                         0, row++, 2, 1);
        grid.add(new HBox(10, btnGuardar, btnCancelar), 0, row, 2, 1);

        dialog.setScene(new Scene(grid, 380, 300));
        dialog.showAndWait();
    }

    /**
     * Persiste cambios de nombre completo y rol en un usuario existente.
     */
    private void guardarCambiosUsuario(Usuario usuario) {
        usuarioRepository.save(usuario);
    }

    // -------------------------------------------------------------------------
    // Tabla
    // -------------------------------------------------------------------------

    private void configurarTabla() {
        colUsername.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getUsername()));
        colNombre.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getNombreCompleto()));
        colRol.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getRol().name()));
        colActivo.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().isActivo() ? "✔ Activo" : "✘ Inactivo"));
        colDebeCambiar.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().isDebeCambiarPassword() ? "Sí" : "No"));

        // Botones de acción por fila
        colAcciones.setCellFactory(col -> new TableCell<>() {
            private final Button btnEditar      = new Button("✏ Editar");
            private final Button btnDesactivar  = new Button("🚫 Desactivar");
            private final Button btnResetPwd    = new Button("🔑 Reset pwd");
            private final HBox hbox = new HBox(4, btnEditar, btnDesactivar, btnResetPwd);

            {
                btnEditar.setStyle("-fx-background-color: #4a90d9; -fx-text-fill: white; -fx-background-radius: 4; -fx-font-size: 11px;");
                btnDesactivar.setStyle("-fx-background-color: #dc3545; -fx-text-fill: white; -fx-background-radius: 4; -fx-font-size: 11px;");
                btnResetPwd.setStyle("-fx-background-color: #ffc107; -fx-text-fill: #333; -fx-background-radius: 4; -fx-font-size: 11px;");
                hbox.setAlignment(Pos.CENTER);

                btnEditar.setOnAction(e -> {
                    Usuario u = getTableView().getItems().get(getIndex());
                    mostrarFormulario(u);
                });

                btnDesactivar.setOnAction(e -> {
                    Usuario u = getTableView().getItems().get(getIndex());
                    // Evitar que el admin se desactive a sí mismo
                    if (usuarioActual != null && u.getId().equals(usuarioActual.getId())) {
                        mostrarError("No puedes desactivar tu propia cuenta.");
                        return;
                    }
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                            "¿Desactivar la cuenta de '" + u.getUsername() + "'?",
                            ButtonType.YES, ButtonType.NO);
                    confirm.setHeaderText(null);
                    confirm.showAndWait().ifPresent(btn -> {
                        if (btn == ButtonType.YES) {
                            try {
                                usuarioService.desactivar(u.getId());
                                cargarUsuarios();
                                mostrarInfo("Cuenta de '" + u.getUsername() + "' desactivada.");
                            } catch (Exception ex) {
                                log.error("Error al desactivar usuario", ex);
                                mostrarError("Error al desactivar: " + ex.getMessage());
                            }
                        }
                    });
                });

                btnResetPwd.setOnAction(e -> {
                    Usuario u = getTableView().getItems().get(getIndex());
                    mostrarDialogoResetPassword(u);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    Usuario u = getTableView().getItems().get(getIndex());
                    // Deshabilitar desactivar si ya está inactivo
                    btnDesactivar.setDisable(!u.isActivo());
                    setGraphic(hbox);
                }
            }
        });
    }

    // -------------------------------------------------------------------------
    // Diálogo restablecer contraseña
    // -------------------------------------------------------------------------

    private void mostrarDialogoResetPassword(Usuario usuario) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Restablecer Contraseña — " + usuario.getUsername());

        PasswordField txtNueva    = new PasswordField();
        PasswordField txtConfirm  = new PasswordField();
        Label lblError = new Label();
        lblError.setStyle("-fx-text-fill: red; -fx-font-size: 12px;");

        Button btnGuardar  = new Button("Restablecer");
        btnGuardar.setStyle("-fx-background-color: #ffc107; -fx-text-fill: #333;");
        Button btnCancelar = new Button("Cancelar");

        btnGuardar.setOnAction(e -> {
            String nueva   = txtNueva.getText();
            String confirm = txtConfirm.getText();

            if (nueva.isEmpty()) {
                lblError.setText("Ingrese la nueva contraseña.");
                return;
            }
            if (nueva.length() < 8) {
                lblError.setText("La contraseña debe tener al menos 8 caracteres.");
                return;
            }
            if (!nueva.equals(confirm)) {
                lblError.setText("Las contraseñas no coinciden.");
                return;
            }

            try {
                // cambiarContrasena establece debeCambiarPassword = false,
                // pero para un reset por admin debe ser true (req. 7.6)
                usuarioService.cambiarContrasena(usuario.getId(), nueva);
                // Marcar que debe cambiar en el siguiente login
                marcarDebeCambiarPassword(usuario.getId());
                dialog.close();
                cargarUsuarios();
                mostrarInfo("Contraseña de '" + usuario.getUsername() + "' restablecida. El usuario deberá cambiarla al iniciar sesión.");
            } catch (Exception ex) {
                log.error("Error al restablecer contraseña", ex);
                lblError.setText("Error: " + ex.getMessage());
            }
        });

        btnCancelar.setOnAction(e -> dialog.close());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        int row = 0;
        grid.add(new Label("Nueva contraseña *:"),    0, row); grid.add(txtNueva,   1, row++);
        grid.add(new Label("Confirmar contraseña *:"), 0, row); grid.add(txtConfirm, 1, row++);
        grid.add(lblError,                             0, row++, 2, 1);
        grid.add(new HBox(10, btnGuardar, btnCancelar), 0, row, 2, 1);

        dialog.setScene(new Scene(grid, 360, 200));
        dialog.showAndWait();
    }

    /**
     * Marca debeCambiarPassword = true en el usuario indicado (requisito 7.6).
     * El UsuarioServiceImpl.cambiarContrasena lo pone en false, así que lo revertimos.
     */
    private void marcarDebeCambiarPassword(Long usuarioId) {
        usuarioRepository.findById(usuarioId).ifPresent(u -> {
            u.setDebeCambiarPassword(true);
            usuarioRepository.save(u);
        });
    }

    // -------------------------------------------------------------------------
    // Utilidades
    // -------------------------------------------------------------------------

    private void cargarUsuarios() {
        List<Usuario> usuarios = usuarioRepository.findAllActivos();
        tablaUsuarios.setItems(FXCollections.observableArrayList(usuarios));
    }

    private void mostrarInfo(String mensaje) {
        lblInfo.setText(mensaje);
        lblInfo.setStyle("-fx-text-fill: #0c5460; -fx-background-color: #d1ecf1; -fx-background-radius: 4; -fx-padding: 8 12 8 12;");
        lblInfo.setVisible(true);
        lblInfo.setManaged(true);
    }

    private void mostrarError(String mensaje) {
        lblInfo.setText(mensaje);
        lblInfo.setStyle("-fx-text-fill: #721c24; -fx-background-color: #f8d7da; -fx-background-radius: 4; -fx-padding: 8 12 8 12;");
        lblInfo.setVisible(true);
        lblInfo.setManaged(true);
    }
}
