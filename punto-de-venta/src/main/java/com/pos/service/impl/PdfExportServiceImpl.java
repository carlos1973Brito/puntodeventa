package com.pos.service.impl;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.pos.dto.ProductoVendidoDTO;
import com.pos.dto.ReporteInventarioDTO;
import com.pos.dto.ReporteVentasDTO;
import com.pos.model.Producto;
import com.pos.model.enums.MetodoPago;
import com.pos.service.PdfExportService;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class PdfExportServiceImpl implements PdfExportService {

    private static final Font FONT_TITULO = new Font(Font.HELVETICA, 18, Font.BOLD);
    private static final Font FONT_SUBTITULO = new Font(Font.HELVETICA, 13, Font.BOLD);
    private static final Font FONT_ENCABEZADO = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE);
    private static final Font FONT_NORMAL = new Font(Font.HELVETICA, 10, Font.NORMAL);
    private static final Color COLOR_ENCABEZADO = new Color(52, 73, 94);

    @Override
    public byte[] exportarReporteVentas(ReporteVentasDTO reporte) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 50, 50);
        try {
            PdfWriter.getInstance(doc, baos);
            doc.open();

            // Título
            doc.add(new Paragraph("Reporte de Ventas", FONT_TITULO));
            doc.add(Chunk.NEWLINE);

            // Rango de fechas y resumen
            doc.add(new Paragraph("Período: " + reporte.desde() + " — " + reporte.hasta(), FONT_NORMAL));
            doc.add(new Paragraph("Total de ventas: " + reporte.totalVentas(), FONT_NORMAL));
            doc.add(new Paragraph("Total recaudado: $" + formatDecimal(reporte.totalRecaudado()), FONT_NORMAL));
            doc.add(Chunk.NEWLINE);

            // Tabla: desglose por método de pago
            doc.add(new Paragraph("Desglose por Método de Pago", FONT_SUBTITULO));
            doc.add(Chunk.NEWLINE);
            PdfPTable tablaPago = crearTabla(2, new String[]{"Método", "Total"});
            if (reporte.porMetodoPago() != null) {
                for (Map.Entry<MetodoPago, BigDecimal> entry : reporte.porMetodoPago().entrySet()) {
                    tablaPago.addCell(celda(entry.getKey().name()));
                    tablaPago.addCell(celda("$" + formatDecimal(entry.getValue())));
                }
            }
            doc.add(tablaPago);
            doc.add(Chunk.NEWLINE);

            // Tabla: top 10 productos más vendidos
            doc.add(new Paragraph("Top 10 Productos Más Vendidos", FONT_SUBTITULO));
            doc.add(Chunk.NEWLINE);
            PdfPTable tablaProductos = crearTabla(3, new String[]{"Producto", "Cantidad", "Total"});
            if (reporte.masVendidos() != null) {
                List<ProductoVendidoDTO> top10 = reporte.masVendidos().stream()
                        .limit(10)
                        .toList();
                for (ProductoVendidoDTO p : top10) {
                    tablaProductos.addCell(celda(p.nombreProducto()));
                    tablaProductos.addCell(celda(String.valueOf(p.cantidadTotal())));
                    tablaProductos.addCell(celda("$" + formatDecimal(p.totalRecaudado())));
                }
            }
            doc.add(tablaProductos);
            doc.add(Chunk.NEWLINE);

            // Tabla: ventas por categoría
            doc.add(new Paragraph("Ventas por Categoría", FONT_SUBTITULO));
            doc.add(Chunk.NEWLINE);
            PdfPTable tablaCategoria = crearTabla(2, new String[]{"Categoría", "Total"});
            if (reporte.porCategoria() != null) {
                for (Map.Entry<String, BigDecimal> entry : reporte.porCategoria().entrySet()) {
                    tablaCategoria.addCell(celda(entry.getKey()));
                    tablaCategoria.addCell(celda("$" + formatDecimal(entry.getValue())));
                }
            }
            doc.add(tablaCategoria);

        } catch (DocumentException e) {
            throw new RuntimeException("Error al generar PDF de reporte de ventas", e);
        } finally {
            if (doc.isOpen()) doc.close();
        }
        return baos.toByteArray();
    }

    @Override
    public byte[] exportarReporteInventario(ReporteInventarioDTO reporte) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 50, 50);
        try {
            PdfWriter.getInstance(doc, baos);
            doc.open();

            // Título
            doc.add(new Paragraph("Reporte de Inventario", FONT_TITULO));
            doc.add(Chunk.NEWLINE);

            // Valor total del inventario
            doc.add(new Paragraph("Valor total del inventario: $" + formatDecimal(reporte.valorTotalInventario()), FONT_NORMAL));
            doc.add(Chunk.NEWLINE);

            // Tabla: todos los productos activos
            doc.add(new Paragraph("Productos Activos", FONT_SUBTITULO));
            doc.add(Chunk.NEWLINE);
            PdfPTable tablaProductos = crearTabla(5,
                    new String[]{"Nombre", "Stock", "Precio Venta", "Precio Costo", "Valor"});
            if (reporte.productosActivos() != null) {
                for (Producto p : reporte.productosActivos()) {
                    BigDecimal valor = p.getPrecioCosto()
                            .multiply(BigDecimal.valueOf(p.getStockActual()));
                    tablaProductos.addCell(celda(p.getNombre()));
                    tablaProductos.addCell(celda(String.valueOf(p.getStockActual())));
                    tablaProductos.addCell(celda("$" + formatDecimal(p.getPrecioVenta())));
                    tablaProductos.addCell(celda("$" + formatDecimal(p.getPrecioCosto())));
                    tablaProductos.addCell(celda("$" + formatDecimal(valor)));
                }
            }
            doc.add(tablaProductos);
            doc.add(Chunk.NEWLINE);

            // Sección: productos bajo stock mínimo
            doc.add(new Paragraph("Productos Bajo Stock Mínimo", FONT_SUBTITULO));
            doc.add(Chunk.NEWLINE);
            PdfPTable tablaBajoStock = crearTabla(5,
                    new String[]{"Nombre", "Stock", "Precio Venta", "Precio Costo", "Valor"});
            if (reporte.productosBajoStock() != null && !reporte.productosBajoStock().isEmpty()) {
                for (Producto p : reporte.productosBajoStock()) {
                    BigDecimal valor = p.getPrecioCosto()
                            .multiply(BigDecimal.valueOf(p.getStockActual()));
                    tablaBajoStock.addCell(celda(p.getNombre()));
                    tablaBajoStock.addCell(celda(String.valueOf(p.getStockActual())));
                    tablaBajoStock.addCell(celda("$" + formatDecimal(p.getPrecioVenta())));
                    tablaBajoStock.addCell(celda("$" + formatDecimal(p.getPrecioCosto())));
                    tablaBajoStock.addCell(celda("$" + formatDecimal(valor)));
                }
            } else {
                tablaBajoStock.addCell(celdaSpan("Sin productos bajo stock mínimo", 5));
            }
            doc.add(tablaBajoStock);

        } catch (DocumentException e) {
            throw new RuntimeException("Error al generar PDF de reporte de inventario", e);
        } finally {
            if (doc.isOpen()) doc.close();
        }
        return baos.toByteArray();
    }

    // --- Helpers ---

    private PdfPTable crearTabla(int columnas, String[] encabezados) throws DocumentException {
        PdfPTable tabla = new PdfPTable(columnas);
        tabla.setWidthPercentage(100);
        for (String enc : encabezados) {
            PdfPCell cell = new PdfPCell(new Phrase(enc, FONT_ENCABEZADO));
            cell.setBackgroundColor(COLOR_ENCABEZADO);
            cell.setPadding(6);
            tabla.addCell(cell);
        }
        return tabla;
    }

    private PdfPCell celda(String texto) {
        PdfPCell cell = new PdfPCell(new Phrase(texto != null ? texto : "", FONT_NORMAL));
        cell.setPadding(5);
        return cell;
    }

    private PdfPCell celdaSpan(String texto, int colspan) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, FONT_NORMAL));
        cell.setColspan(colspan);
        cell.setPadding(5);
        return cell;
    }

    private String formatDecimal(BigDecimal value) {
        if (value == null) return "0.00";
        return String.format("%.2f", value);
    }
}
