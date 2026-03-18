package com.pos.exception;

public class CredencialesInvalidasException extends PosException {

    public CredencialesInvalidasException() {
        super("Credenciales inválidas. Verifique su usuario y contraseña.");
    }
}
