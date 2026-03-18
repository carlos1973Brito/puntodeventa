package com.pos.dto;

import com.pos.model.enums.MetodoPago;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record ReporteVentasDTO(
        LocalDate desde,
        LocalDate hasta,
        int totalVentas,
        BigDecimal totalRecaudado,
        Map<MetodoPago, BigDecimal> porMetodoPago,
        List<ProductoVendidoDTO> masVendidos,
        Map<String, BigDecimal> porCategoria
) {}
