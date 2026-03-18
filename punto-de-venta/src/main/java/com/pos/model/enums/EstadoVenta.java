package com.pos.model.enums;

public enum EstadoVenta {
    EN_CURSO,
    COMPLETADA,
    CANCELADA,
    CREDITO,      // Venta a crédito con saldo pendiente
    DEVUELTA      // Venta revertida/devuelta
}
