package com.pos.exception;

import java.time.LocalDateTime;

public class UsuarioBloqueadoException extends PosException {

    private final LocalDateTime bloqueadoHasta;

    public UsuarioBloqueadoException(LocalDateTime bloqueadoHasta) {
        super("Usuario bloqueado hasta: " + bloqueadoHasta);
        this.bloqueadoHasta = bloqueadoHasta;
    }

    public LocalDateTime getBloqueadoHasta() {
        return bloqueadoHasta;
    }
}
