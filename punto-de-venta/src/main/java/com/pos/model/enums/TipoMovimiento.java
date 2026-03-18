package com.pos.model.enums;

/**
 * Tipos de movimiento de stock registrados en el historial de inventario.
 *
 * <ul>
 *   <li>{@code ENTRADA} — ingreso de mercancía o reposición por devolución</li>
 *   <li>{@code SALIDA} — descuento automático al completar una venta</li>
 *   <li>{@code AJUSTE} — corrección manual del stock (requiere justificación)</li>
 * </ul>
 */
public enum TipoMovimiento {
    ENTRADA,
    SALIDA,
    AJUSTE
}
