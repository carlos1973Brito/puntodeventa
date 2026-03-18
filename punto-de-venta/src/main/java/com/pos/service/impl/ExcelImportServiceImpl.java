package com.pos.service.impl;

import com.pos.dto.FilaExcel;
import com.pos.dto.ImportPreview;
import com.pos.dto.ImportResult;
import com.pos.model.LineaVenta;
import com.pos.model.Producto;
import com.pos.model.Usuario;
import com.pos.model.Venta;
import com.pos.model.enums.EstadoVenta;
import com.pos.model.enums.MetodoPago;
import com.pos.repository.ProductoRepository;
import com.pos.repository.UsuarioRepository;
import com.pos.repository.VentaRepository;
import com.pos.service.ExcelImportService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

public class ExcelImportServiceImpl implements ExcelImportService {

    private static final Logger log = LoggerFactory.getLogger(ExcelImportServiceImpl.class);

    private final ProductoRepository productoRepository;
    private final VentaRepository ventaRepository;
    private final UsuarioRepository usuarioRepository;

    // Mapa de nombres de mes en español → número de mes
    private static final Map<String, Integer> MESES = new LinkedHashMap<>();

    static {
        MESES.put("enero",      1);
        MESES.put("ene",        1);
        MESES.put("febrero",    2);
        MESES.put("feb",        2);
        MESES.put("marzo",      3);
        MESES.put("mar",        3);
        MESES.put("abril",      4);
        MESES.put("abr",        4);
        MESES.put("mayo",       5);
        MESES.put("may",        5);
        MESES.put("junio",      6);
        MESES.put("jun",        6);
        MESES.put("julio",      7);
        MESES.put("jul",        7);
        MESES.put("agosto",     8);
        MESES.put("ago",        8);
        MESES.put("septiembre", 9);
        MESES.put("sep",        9);
        MESES.put("sept",       9);
        MESES.put("octubre",    10);
        MESES.put("oct",        10);
        MESES.put("noviembre",  11);
        MESES.put("nov",        11);
        MESES.put("diciembre",  12);
        MESES.put("dic",        12);
    }

    public ExcelImportServiceImpl(ProductoRepository productoRepository,
                                   VentaRepository ventaRepository,
                                   UsuarioRepository usuarioRepository) {
        this.productoRepository = productoRepository;
        this.ventaRepository = ventaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    // -------------------------------------------------------------------------
    // Interfaz pública
    // -------------------------------------------------------------------------

    @Override
    public ImportPreview previsualizarExcel(File archivo) {
        List<FilaExcel> filas = new ArrayList<>();
        List<String> errores = new ArrayList<>();

        // Aumentar límite de entradas ZIP para archivos Excel grandes
        ZipSecureFile.setMaxFileCount(Long.MAX_VALUE);

        try (Workbook workbook = WorkbookFactory.create(archivo)) {
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet hoja = workbook.getSheetAt(i);
                String nombreHoja = hoja.getSheetName();

                Optional<LocalDate> fechaHoja = parsearFechaHoja(nombreHoja);
                if (fechaHoja.isEmpty()) {
                    String msg = "Hoja '" + nombreHoja + "': no se pudo interpretar como fecha. Se omite.";
                    errores.add(msg);
                    log.warn(msg);
                    continue;
                }

                procesarHoja(hoja, fechaHoja.get(), filas, errores);
            }
        } catch (Exception e) {
            log.error("Error al abrir el archivo Excel: {}", e.getMessage(), e);
            errores.add("Error al abrir el archivo: " + e.getMessage());
            return new ImportPreview(filas, errores, 0, 0);
        }

        // Calcular productos nuevos (artículos únicos que no existen en el repositorio)
        Set<String> articulosUnicos = new LinkedHashSet<>();
        for (FilaExcel fila : filas) {
            if (fila.valida()) {
                articulosUnicos.add(fila.articulo().trim().toLowerCase());
            }
        }

        int productosNuevos = 0;
        for (String articulo : articulosUnicos) {
            boolean existe = productoRepository.findAllActivos().stream()
                    .anyMatch(p -> p.getNombre().trim().equalsIgnoreCase(articulo));
            if (!existe) {
                productosNuevos++;
            }
        }

        long ventasARegistrar = filas.stream().filter(FilaExcel::valida).count();

        return new ImportPreview(filas, errores, productosNuevos, (int) ventasARegistrar);
    }

    @Override
    public ImportResult importarExcel(File archivo) {
        // 1. Obtener filas parseadas via previsualizarExcel
        ImportPreview preview = previsualizarExcel(archivo);

        List<String> detalleErrores = new ArrayList<>(preview.errores());
        int productosCreados = 0;
        int ventasRegistradas = 0;

        // Separar filas válidas e inválidas
        List<FilaExcel> filasValidas = preview.filas().stream()
                .filter(FilaExcel::valida)
                .toList();
        long filasConError = preview.filas().stream().filter(f -> !f.valida()).count()
                + preview.errores().stream()
                        .filter(e -> e.startsWith("Hoja '") && e.contains("no se pudo interpretar"))
                        .count();

        // Buscar usuario "sistema" o "admin" para cajero (puede ser null)
        Usuario cajeroSistema = usuarioRepository.findByUsername("sistema")
                .or(() -> usuarioRepository.findByUsername("admin"))
                .orElse(null);

        // 2. Deduplicar artículos únicos (case-insensitive) y crear productos nuevos
        // Mapa: nombre_lower → precio de primera aparición
        Map<String, BigDecimal> articulosPrecio = new LinkedHashMap<>();
        for (FilaExcel fila : filasValidas) {
            String key = fila.articulo().trim().toLowerCase();
            articulosPrecio.putIfAbsent(key, fila.precioVenta());
        }

        // Mapa: nombre_lower → Producto (existente o recién creado)
        Map<String, Producto> productosPorNombre = new HashMap<>();

        for (Map.Entry<String, BigDecimal> entry : articulosPrecio.entrySet()) {
            String nombreLower = entry.getKey();
            BigDecimal precio = entry.getValue();

            // Buscar si ya existe (case-insensitive)
            Optional<Producto> existente = productoRepository.findAllActivos().stream()
                    .filter(p -> p.getNombre().trim().equalsIgnoreCase(nombreLower))
                    .findFirst();

            if (existente.isPresent()) {
                productosPorNombre.put(nombreLower, existente.get());
            } else {
                // Capitalizar nombre
                String nombreCapitalizado = capitalizar(entry.getKey());
                Producto nuevo = new Producto();
                nuevo.setNombre(nombreCapitalizado);
                nuevo.setPrecioVenta(precio);
                nuevo.setPrecioCosto(BigDecimal.ZERO);
                nuevo.setStockActual(100);
                nuevo.setStockMinimo(5);
                nuevo.setActivo(true);
                nuevo.setCategoria(null);
                nuevo.setCodigoBarras(null);
                Producto guardado = productoRepository.save(nuevo);
                productosPorNombre.put(nombreLower, guardado);
                productosCreados++;
            }
        }

        // 3. Registrar una venta por cada fila válida
        for (FilaExcel fila : filasValidas) {
            String nombreLower = fila.articulo().trim().toLowerCase();
            Producto producto = productosPorNombre.get(nombreLower);
            if (producto == null) {
                detalleErrores.add("No se encontró producto para artículo: " + fila.articulo());
                continue;
            }

            try {
                Venta venta = new Venta();
                venta.setCajero(cajeroSistema);
                venta.setFecha(fila.fechaHoraVenta());
                venta.setEstado(EstadoVenta.COMPLETADA);
                venta.setMetodoPago(MetodoPago.EFECTIVO);
                venta.setMontoRecibido(fila.precioVenta());
                venta.setCambio(BigDecimal.ZERO);
                venta.setTotal(fila.precioVenta());

                LineaVenta linea = new LineaVenta();
                linea.setProducto(producto);
                linea.setNombreProducto(producto.getNombre());
                linea.setPrecioUnitario(fila.precioVenta());
                linea.setPrecioCosto(BigDecimal.ZERO);
                linea.setCantidad(1);
                linea.setSubtotal(fila.precioVenta());

                venta.agregarLinea(linea);
                ventaRepository.save(venta);
                ventasRegistradas++;
            } catch (Exception e) {
                String msg = "Error al registrar venta para artículo '" + fila.articulo() + "': " + e.getMessage();
                detalleErrores.add(msg);
                log.error(msg, e);
            }
        }

        return new ImportResult(productosCreados, ventasRegistradas, (int) filasConError, detalleErrores);
    }

    /** Capitaliza la primera letra de cada palabra. */
    private String capitalizar(String texto) {
        if (texto == null || texto.isBlank()) return texto;
        String[] palabras = texto.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String palabra : palabras) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(palabra.charAt(0)));
            if (palabra.length() > 1) sb.append(palabra.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Lógica interna
    // -------------------------------------------------------------------------

    /**
     * Procesa todas las filas de una hoja, saltando la primera si parece encabezado.
     */
    private void procesarHoja(Sheet hoja, LocalDate fechaHoja,
                               List<FilaExcel> filas, List<String> errores) {
        int primeraFila = hoja.getFirstRowNum();
        int ultimaFila  = hoja.getLastRowNum();

        // Determinar si la primera fila es encabezado (texto en columna 0 y sin precio numérico)
        int filaInicio = primeraFila;
        if (ultimaFila >= primeraFila) {
            Row primera = hoja.getRow(primeraFila);
            if (primera != null && esEncabezado(primera)) {
                filaInicio = primeraFila + 1;
            }
        }

        for (int r = filaInicio; r <= ultimaFila; r++) {
            Row fila = hoja.getRow(r);
            if (fila == null || esFilaVacia(fila)) {
                continue;
            }
            FilaExcel fe = parsearFila(fila, fechaHoja, r + 1);
            filas.add(fe);
            if (!fe.valida()) {
                errores.add("Hoja '" + hoja.getSheetName() + "', fila " + (r + 1) + ": " + fe.mensajeError());
            }
        }
    }

    /**
     * Determina si una fila parece ser encabezado:
     * columna 0 es texto y columna 1 no es numérica.
     */
    private boolean esEncabezado(Row fila) {
        Cell col0 = fila.getCell(0);
        Cell col1 = fila.getCell(1);
        if (col0 == null) return false;
        boolean col0EsTexto = col0.getCellType() == CellType.STRING;
        boolean col1NoEsNumero = col1 == null || col1.getCellType() != CellType.NUMERIC;
        return col0EsTexto && col1NoEsNumero;
    }

    private boolean esFilaVacia(Row fila) {
        for (int c = 0; c < 3; c++) {
            Cell cell = fila.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }

    /**
     * Parsea una fila individual y retorna un FilaExcel (válida o inválida).
     */
    private FilaExcel parsearFila(Row fila, LocalDate fechaHoja, int numFila) {
        // --- Columna 0: artículo ---
        String articulo = leerCeldaComoString(fila.getCell(0));
        if (articulo == null || articulo.isBlank()) {
            return filaInvalida(null, null, null, null,
                    "Artículo vacío en fila " + numFila);
        }

        // --- Columna 1: precio ---
        BigDecimal precio = leerCeldaComoDecimal(fila.getCell(1));
        if (precio == null) {
            return filaInvalida(articulo, null, null, null,
                    "Precio no numérico en fila " + numFila);
        }
        if (precio.compareTo(BigDecimal.ZERO) <= 0) {
            return filaInvalida(articulo, precio, null, null,
                    "Precio debe ser mayor a 0 en fila " + numFila);
        }

        // --- Columna 2: hora ---
        String horaStr = leerCeldaComoString(fila.getCell(2));
        if (horaStr == null || horaStr.isBlank()) {
            return filaInvalida(articulo, precio, null, null,
                    "Hora vacía en fila " + numFila);
        }

        Optional<LocalTime> hora = parsearHora(horaStr);
        if (hora.isEmpty()) {
            return filaInvalida(articulo, precio, null, null,
                    "Formato de hora incorrecto '" + horaStr + "' en fila " + numFila);
        }

        LocalDateTime fechaHora = LocalDateTime.of(fechaHoja, hora.get());
        return new FilaExcel(articulo.trim(), precio, hora.get(), fechaHora, true, null);
    }

    private FilaExcel filaInvalida(String articulo, BigDecimal precio,
                                    LocalTime hora, LocalDateTime fechaHora,
                                    String mensaje) {
        return new FilaExcel(articulo, precio, hora, fechaHora, false, mensaje);
    }

    // -------------------------------------------------------------------------
    // Parseo de hora AM/PM
    // -------------------------------------------------------------------------

    /**
     * Parsea hora en formato "HH:mm:ss a. m." o "HH:mm:ss p. m." (español mexicano).
     * Normaliza el string antes de parsear.
     */
    Optional<LocalTime> parsearHora(String horaStr) {
        if (horaStr == null) return Optional.empty();

        // Normalizar: "a. m." → "AM", "p. m." → "PM"
        String normalizado = horaStr.trim()
                .replaceAll("(?i)a\\.\\s*m\\.?", "AM")
                .replaceAll("(?i)p\\.\\s*m\\.?", "PM");

        // Intentar con formato 12h
        DateTimeFormatter fmt12 = DateTimeFormatter.ofPattern("hh:mm:ss a", Locale.ENGLISH);
        try {
            return Optional.of(LocalTime.parse(normalizado, fmt12));
        } catch (DateTimeParseException ignored) {
            // intentar sin segundos
        }

        DateTimeFormatter fmt12NoSec = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);
        try {
            return Optional.of(LocalTime.parse(normalizado, fmt12NoSec));
        } catch (DateTimeParseException ignored) {
            // intentar formato 24h como fallback
        }

        DateTimeFormatter fmt24 = DateTimeFormatter.ofPattern("HH:mm:ss");
        try {
            return Optional.of(LocalTime.parse(normalizado, fmt24));
        } catch (DateTimeParseException ignored) {
            return Optional.empty();
        }
    }

    // -------------------------------------------------------------------------
    // Parseo de nombre de hoja → LocalDate
    // -------------------------------------------------------------------------

    /**
     * Parsea nombres como "Enero 15", "Feb 3", "Marzo 10", etc.
     * Usa el año actual.
     */
    Optional<LocalDate> parsearFechaHoja(String nombreHoja) {
        if (nombreHoja == null || nombreHoja.isBlank()) return Optional.empty();

        String[] partes = nombreHoja.trim().split("\\s+");
        if (partes.length < 2) return Optional.empty();

        String mesStr = partes[0].toLowerCase();
        Integer mes = MESES.get(mesStr);
        if (mes == null) return Optional.empty();

        int dia;
        try {
            dia = Integer.parseInt(partes[1]);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }

        int anio = LocalDate.now().getYear();
        try {
            return Optional.of(LocalDate.of(anio, mes, dia));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    // -------------------------------------------------------------------------
    // Utilidades de lectura de celdas
    // -------------------------------------------------------------------------

    private String leerCeldaComoString(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue();
            case NUMERIC -> {
                // Puede ser una hora numérica de Excel
                if (DateUtil.isCellDateFormatted(cell)) {
                    // Convertir fecha/hora de Excel a string de hora
                    Date date = cell.getDateCellValue();
                    java.util.Calendar cal = java.util.Calendar.getInstance();
                    cal.setTime(date);
                    int h = cal.get(java.util.Calendar.HOUR_OF_DAY);
                    int m = cal.get(java.util.Calendar.MINUTE);
                    int s = cal.get(java.util.Calendar.SECOND);
                    String ampm = h >= 12 ? "PM" : "AM";
                    int h12 = h % 12;
                    if (h12 == 0) h12 = 12;
                    yield String.format("%02d:%02d:%02d %s", h12, m, s, ampm);
                }
                yield String.valueOf((long) cell.getNumericCellValue());
            }
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception e) {
                    yield String.valueOf(cell.getNumericCellValue());
                }
            }
            default -> null;
        };
    }

    private BigDecimal leerCeldaComoDecimal(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue());
            case STRING  -> {
                try {
                    yield new BigDecimal(cell.getStringCellValue().trim());
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
            case FORMULA -> {
                try {
                    yield BigDecimal.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    yield null;
                }
            }
            default -> null;
        };
    }
}
