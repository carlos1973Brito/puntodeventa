package com.pos.service;

import com.pos.dto.ImportPreview;
import com.pos.dto.ImportResult;

import java.io.File;

public interface ExcelImportService {
    ImportPreview previsualizarExcel(File archivo);
    ImportResult importarExcel(File archivo);
}
