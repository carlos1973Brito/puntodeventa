package com.pos.service;

import com.pos.dto.ReporteInventarioDTO;
import com.pos.dto.ReporteVentasDTO;

public interface PdfExportService {
    byte[] exportarReporteVentas(ReporteVentasDTO reporte);
    byte[] exportarReporteInventario(ReporteInventarioDTO reporte);
}
