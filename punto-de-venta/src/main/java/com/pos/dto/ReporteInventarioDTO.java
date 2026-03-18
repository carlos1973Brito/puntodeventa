package com.pos.dto;

import com.pos.model.Producto;

import java.math.BigDecimal;
import java.util.List;

public record ReporteInventarioDTO(
        List<Producto> productosActivos,
        BigDecimal valorTotalInventario,
        List<Producto> productosBajoStock
) {}
