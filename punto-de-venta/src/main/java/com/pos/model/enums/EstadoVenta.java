package com.pos.model.enums;

/**
 * Estados posibles de una venta a lo largo de su ciclo de vida.
 *
 * <ul>
 *   <li>{@code EN_CURSO} — venta abierta, aún no cobrada</li>
 *   <li>{@code COMPLETADA} — cobrada exitosamente</li>
 *   <li>{@code CANCELADA} — cancelada antes de cobrar (no afecta stock)</li>
 *   <li>{@code CREDITO} — cobrada a crédito, tiene saldo pendiente</li>
 *   <li>{@code DEVUELTA} — devuelta; el stock fue repuesto</li>
 * </ul>
 */
public enum EstadoVenta {
    EN_CURSO,
    COMPLETADA,
    CANCELADA,
    CREDITO,      // Venta a crédito con saldo pendiente
    DEVUELTA      // Venta revertida/devuelta
}
