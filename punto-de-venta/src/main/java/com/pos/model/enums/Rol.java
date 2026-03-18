package com.pos.model.enums;

/**
 * Roles de usuario en el sistema.
 *
 * <ul>
 *   <li>{@code ADMINISTRADOR} — acceso completo, incluyendo gestión de usuarios</li>
 *   <li>{@code CAJERO} — acceso a ventas, inventario y reportes; sin gestión de usuarios</li>
 * </ul>
 */
public enum Rol {
    ADMINISTRADOR,
    CAJERO
}
