package com.pos.dto;

import java.math.BigDecimal;

public record ProductoVendidoDTO(
        String nombreProducto,
        int cantidadTotal,
        BigDecimal totalRecaudado
) {}
