package com.pos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record FilaExcel(
        String articulo,
        BigDecimal precioVenta,
        LocalTime horaVenta,
        LocalDateTime fechaHoraVenta,
        boolean valida,
        String mensajeError
) {}
