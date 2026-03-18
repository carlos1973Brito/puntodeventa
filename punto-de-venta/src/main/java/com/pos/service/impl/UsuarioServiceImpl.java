package com.pos.service.impl;

import com.pos.dto.UsuarioDTO;
import com.pos.exception.CredencialesInvalidasException;
import com.pos.exception.PosException;
import com.pos.exception.UsuarioBloqueadoException;
import com.pos.model.Usuario;
import com.pos.repository.UsuarioRepository;
import com.pos.service.UsuarioService;
import org.mindrot.jbcrypt.BCrypt;

import java.time.LocalDateTime;
import java.util.Optional;

public class UsuarioServiceImpl implements UsuarioService {

    private static final int MAX_INTENTOS = 5;
    private static final int MINUTOS_BLOQUEO = 15;

    private final UsuarioRepository usuarioRepository;

    public UsuarioServiceImpl(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public Usuario crear(UsuarioDTO dto) {
        String hash = BCrypt.hashpw(dto.password(), BCrypt.gensalt());
        Usuario usuario = new Usuario(dto.username(), hash, dto.nombreCompleto(), dto.rol());
        return usuarioRepository.save(usuario);
    }

    @Override
    public void desactivar(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + id));
        usuario.setActivo(false);
        usuarioRepository.save(usuario);
    }

    @Override
    public void cambiarContrasena(Long id, String nuevaContrasena) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + id));
        usuario.setPasswordHash(BCrypt.hashpw(nuevaContrasena, BCrypt.gensalt()));
        usuario.setDebeCambiarPassword(false);
        usuario.setIntentosFallidos(0);
        usuarioRepository.save(usuario);
    }

    @Override
    public Optional<Usuario> autenticar(String username, String password) throws PosException {
        Optional<Usuario> opt = usuarioRepository.findByUsername(username);

        if (opt.isEmpty()) {
            throw new CredencialesInvalidasException();
        }

        Usuario usuario = opt.get();

        if (!usuario.isActivo()) {
            throw new CredencialesInvalidasException();
        }

        if (estaBloqueado(username)) {
            throw new UsuarioBloqueadoException(usuario.getBloqueadoHasta());
        }

        if (!BCrypt.checkpw(password, usuario.getPasswordHash())) {
            registrarIntentoFallido(username);
            throw new CredencialesInvalidasException();
        }

        // Autenticación exitosa: resetear intentos fallidos
        usuario.setIntentosFallidos(0);
        usuarioRepository.save(usuario);

        return Optional.of(usuario);
    }

    @Override
    public void registrarIntentoFallido(String username) {
        usuarioRepository.findByUsername(username).ifPresent(usuario -> {
            usuario.setIntentosFallidos(usuario.getIntentosFallidos() + 1);
            if (usuario.getIntentosFallidos() >= MAX_INTENTOS) {
                usuario.setBloqueadoHasta(LocalDateTime.now().plusMinutes(MINUTOS_BLOQUEO));
            }
            usuarioRepository.save(usuario);
        });
    }

    @Override
    public boolean estaBloqueado(String username) {
        Optional<Usuario> opt = usuarioRepository.findByUsername(username);
        if (opt.isEmpty()) {
            return false;
        }

        Usuario usuario = opt.get();
        LocalDateTime bloqueadoHasta = usuario.getBloqueadoHasta();

        if (bloqueadoHasta == null) {
            return false;
        }

        if (bloqueadoHasta.isAfter(LocalDateTime.now())) {
            return true;
        }

        // El bloqueo ya expiró: limpiar
        usuario.setBloqueadoHasta(null);
        usuario.setIntentosFallidos(0);
        usuarioRepository.save(usuario);
        return false;
    }
}
