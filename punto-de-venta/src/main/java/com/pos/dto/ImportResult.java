package com.pos.dto;

import java.util.List;

public record ImportResult(
        int productosCreados,
        int ventasRegistradas,
        int filasConError,
        List<String> detalleErrores
) {}
