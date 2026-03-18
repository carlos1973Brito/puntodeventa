package com.pos.dto;

import com.pos.model.enums.Rol;

public record UsuarioDTO(
        String username,
        String password,
        String nombreCompleto,
        Rol rol
) {}
