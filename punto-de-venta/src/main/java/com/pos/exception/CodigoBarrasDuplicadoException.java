package com.pos.exception;

public class CodigoBarrasDuplicadoException extends PosException {

    private final String codigoBarras;

    public CodigoBarrasDuplicadoException(String codigoBarras) {
        super("El código de barras ya está en uso: " + codigoBarras);
        this.codigoBarras = codigoBarras;
    }

    public String getCodigoBarras() {
        return codigoBarras;
    }
}
