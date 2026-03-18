package com.pos.dto;

import java.math.BigDecimal;

public record ProductoDTO(
        String nombre,
        String descripcion,
        String codigoBarras,
        BigDecimal precioVenta,
        BigDecimal precioCosto,
        int stockInicial,
        int stockMinimo,
        Long categoriaId
) {}
