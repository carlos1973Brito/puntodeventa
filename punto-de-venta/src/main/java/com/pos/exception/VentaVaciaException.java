package com.pos.exception;

public class VentaVaciaException extends PosException {

    public VentaVaciaException() {
        super("La venta no tiene líneas de venta. Agregue al menos un producto.");
    }
}
