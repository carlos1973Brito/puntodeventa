package com.pos.exception;

import java.math.BigDecimal;

public class PagoInsuficienteException extends PosException {

    private final BigDecimal totalVenta;
    private final BigDecimal montoRecibido;

    public PagoInsuficienteException(BigDecimal totalVenta, BigDecimal montoRecibido) {
        super("Pago insuficiente. Total: " + totalVenta + ", recibido: " + montoRecibido);
        this.totalVenta = totalVenta;
        this.montoRecibido = montoRecibido;
    }

    public BigDecimal getTotalVenta() {
        return totalVenta;
    }

    public BigDecimal getMontoRecibido() {
        return montoRecibido;
    }
}
