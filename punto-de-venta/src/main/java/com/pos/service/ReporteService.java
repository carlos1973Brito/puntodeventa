package com.pos.service;

import com.pos.dto.ReporteInventarioDTO;
import com.pos.dto.ReporteVentasDTO;

import java.time.LocalDate;

public interface ReporteService {
    ReporteVentasDTO generarReporteVentas(LocalDate desde, LocalDate hasta);
    ReporteInventarioDTO generarReporteInventario();
}
