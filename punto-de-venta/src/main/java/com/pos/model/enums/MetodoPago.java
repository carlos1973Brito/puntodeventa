package com.pos.model.enums;

/**
 * Métodos de pago aceptados en el POS.
 *
 * <ul>
 *   <li>{@code EFECTIVO} — calcula cambio automáticamente</li>
 *   <li>{@code TARJETA_CREDITO} / {@code TARJETA_DEBITO} — sin cálculo de cambio</li>
 *   <li>{@code CREDITO} — venta a crédito/apartado con saldo pendiente</li>
 * </ul>
 */
public enum MetodoPago {
    EFECTIVO,
    TARJETA_CREDITO,
    TARJETA_DEBITO,
    CREDITO         // Venta a crédito / apartado
}
