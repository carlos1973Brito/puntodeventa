package com.pos.dto;

import java.util.List;

public record ImportPreview(
        List<FilaExcel> filas,
        List<String> errores,
        int productosNuevos,
        int ventasARegistrar
) {}
