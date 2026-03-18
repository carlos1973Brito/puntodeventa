package com.pos.service;

import com.pos.dto.UsuarioDTO;
import com.pos.exception.PosException;
import com.pos.model.Usuario;

import java.util.Optional;

/**
 * Contrato del servicio de usuarios.
 * Maneja autenticación BCrypt, bloqueo por intentos fallidos y gestión de cuentas.
 */
public interface UsuarioService {
    Usuario crear(UsuarioDTO dto);
    void desactivar(Long id);
    void cambiarContrasena(Long id, String nuevaContrasena);
    Optional<Usuario> autenticar(String username, String password) throws PosException;
    void registrarIntentoFallido(String username);
    boolean estaBloqueado(String username);
}
