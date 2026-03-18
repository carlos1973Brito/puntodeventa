package com.pos.service.impl;

import com.pos.model.LineaVenta;
import com.pos.model.Venta;
import com.pos.model.enums.MetodoPago;
import com.pos.service.ConfiguracionService;
import com.pos.service.TicketPrinterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.print.*;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Implementación de impresión ESC/POS usando javax.print (raw bytes).
 * Compatible con impresoras térmicas de 58mm y 80mm instaladas en Windows.
 */
public class TicketPrinterServiceImpl implements TicketPrinterService {

    private static final Logger log = LoggerFactory.getLogger(TicketPrinterServiceImpl.class);
    private static final NumberFormat CURRENCY = NumberFormat.getCurrencyInstance(new Locale("es", "MX"));
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // ── Comandos ESC/POS ──────────────────────────────────────────────────────
    private static final byte ESC  = 0x1B;
    private static final byte GS   = 0x1D;
    private static final byte LF   = 0x0A;

    /** Inicializar impresora */
    private static final byte[] INIT          = { ESC, '@' };
    /** Alinear izquierda */
    private static final byte[] ALIGN_LEFT    = { ESC, 'a', 0 };
    /** Alinear centro */
    private static final byte[] ALIGN_CENTER  = { ESC, 'a', 1 };
    /** Alinear derecha */
    private static final byte[] ALIGN_RIGHT   = { ESC, 'a', 2 };
    /** Texto normal */
    private static final byte[] FONT_NORMAL   = { ESC, '!', 0x00 };
    /** Texto negrita */
    private static final byte[] FONT_BOLD     = { ESC, '!', 0x08 };
    /** Texto doble alto + negrita (para totales) */
    private static final byte[] FONT_DOUBLE   = { ESC, '!', 0x30 };
    /** Corte parcial de papel */
    private static final byte[] CUT_PARTIAL   = { GS, 'V', 66, 0 };

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void imprimirTicket(Venta venta) throws Exception {
        ConfiguracionService config = new ConfiguracionService();
        String nombreImpresora = config.get(ConfiguracionService.PRINTER_NAME, "").trim();
        if (nombreImpresora.isEmpty()) {
            throw new IllegalStateException(
                "No hay impresora configurada. Ve a Configuración → Impresora.");
        }

        int ancho;
        try {
            ancho = Integer.parseInt(config.get(ConfiguracionService.PRINTER_WIDTH, "32"));
        } catch (NumberFormatException e) {
            ancho = 32;
        }
        String negocio   = config.get(ConfiguracionService.NEGOCIO_NOMBRE,    "Mi Negocio");
        String direccion = config.get(ConfiguracionService.NEGOCIO_DIRECCION,  "");
        String telefono  = config.get(ConfiguracionService.NEGOCIO_TELEFONO,   "");
        String mensaje   = config.get(ConfiguracionService.NEGOCIO_MENSAJE,    "¡Gracias por su compra!");

        byte[] ticket = construirTicket(venta, ancho, negocio, direccion, telefono, mensaje);
        enviarAImpresora(nombreImpresora, ticket);
        log.info("Ticket impreso en '{}' ({} bytes)", nombreImpresora, ticket.length);
    }

    @Override
    public void imprimirPrueba(String nombreImpresora) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        write(buf, INIT);
        write(buf, ALIGN_CENTER);
        write(buf, FONT_BOLD);
        writeLine(buf, "=== PRUEBA DE IMPRESION ===", "CP850");
        write(buf, FONT_NORMAL);
        writeLine(buf, "Impresora: " + nombreImpresora, "CP850");
        writeLine(buf, "Sistema POS listo.", "CP850");
        writeLine(buf, "", "CP850");
        write(buf, CUT_PARTIAL);
        enviarAImpresora(nombreImpresora, buf.toByteArray());
    }

    @Override
    public List<String> listarImpresoras() {
        List<String> nombres = new ArrayList<>();
        PrintService[] servicios = PrintServiceLookup.lookupPrintServices(null, null);
        for (PrintService ps : servicios) {
            nombres.add(ps.getName());
        }
        return nombres;
    }

    // ── Construcción del ticket ───────────────────────────────────────────────

    private byte[] construirTicket(Venta venta, int ancho,
                                   String negocio, String direccion,
                                   String telefono, String mensaje) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        String enc = "CP850"; // codepage compatible con la mayoría de térmicas

        write(buf, INIT);

        // ── Encabezado ──
        write(buf, ALIGN_CENTER);
        write(buf, FONT_BOLD);
        writeLine(buf, centrar(negocio, ancho), enc);
        write(buf, FONT_NORMAL);
        if (!direccion.isBlank()) writeLine(buf, centrar(direccion, ancho), enc);
        if (!telefono.isBlank())  writeLine(buf, centrar("Tel: " + telefono, ancho), enc);
        writeLine(buf, separador('-', ancho), enc);

        // ── Info de venta ──
        write(buf, ALIGN_LEFT);
        writeLine(buf, "Venta #" + venta.getId(), enc);
        if (venta.getFecha() != null)
            writeLine(buf, "Fecha: " + venta.getFecha().format(FMT), enc);
        if (venta.getCajero() != null)
            writeLine(buf, "Cajero: " + venta.getCajero().getNombreCompleto(), enc);
        writeLine(buf, separador('-', ancho), enc);

        // ── Líneas de venta ──
        // Cabecera columnas
        writeLine(buf, padRight("Producto", ancho - 18) + padLeft("Cant", 4)
                + padLeft("P.Unit", 7) + padLeft("Sub", 7), enc);
        writeLine(buf, separador('-', ancho), enc);

        for (LineaVenta linea : venta.getLineas()) {
            String nombre = truncar(linea.getNombreProducto(), ancho - 18);
            String cant   = padLeft(String.valueOf(linea.getCantidad()), 4);
            String precio = padLeft(formatMonto(linea.getPrecioUnitario()), 7);
            String sub    = padLeft(formatMonto(linea.getSubtotal()), 7);
            writeLine(buf, padRight(nombre, ancho - 18) + cant + precio + sub, enc);
        }

        writeLine(buf, separador('=', ancho), enc);

        // ── Total ──
        write(buf, ALIGN_RIGHT);
        write(buf, FONT_DOUBLE);
        writeLine(buf, "TOTAL: " + CURRENCY.format(venta.getTotal()), enc);
        write(buf, FONT_NORMAL);

        // ── Pago ──
        write(buf, ALIGN_LEFT);
        if (venta.getMetodoPago() != null) {
            writeLine(buf, "Metodo: " + labelMetodo(venta.getMetodoPago()), enc);
        }
        if (venta.getMetodoPago() == MetodoPago.EFECTIVO) {
            if (venta.getMontoRecibido() != null)
                writeLine(buf, "Recibido: " + CURRENCY.format(venta.getMontoRecibido()), enc);
            if (venta.getCambio() != null)
                writeLine(buf, "Cambio:   " + CURRENCY.format(venta.getCambio()), enc);
        }

        writeLine(buf, separador('-', ancho), enc);

        // ── Pie ──
        write(buf, ALIGN_CENTER);
        writeLine(buf, mensaje, enc);
        writeLine(buf, "", enc);
        writeLine(buf, "", enc);
        writeLine(buf, "", enc);

        // ── Corte ──
        write(buf, CUT_PARTIAL);

        return buf.toByteArray();
    }

    // ── Envío a impresora ─────────────────────────────────────────────────────

    private void enviarAImpresora(String nombreImpresora, byte[] datos) throws Exception {
        PrintService servicio = encontrarImpresora(nombreImpresora);
        if (servicio == null) {
            throw new IllegalArgumentException(
                "Impresora no encontrada: '" + nombreImpresora + "'");
        }

        DocFlavor flavor = DocFlavor.BYTE_ARRAY.AUTOSENSE;
        Doc doc = new SimpleDoc(datos, flavor, null);
        PrintRequestAttributeSet attrs = new HashPrintRequestAttributeSet();
        DocPrintJob job = servicio.createPrintJob();
        job.print(doc, attrs);
    }

    private PrintService encontrarImpresora(String nombre) {
        for (PrintService ps : PrintServiceLookup.lookupPrintServices(null, null)) {
            if (ps.getName().equalsIgnoreCase(nombre)) return ps;
        }
        return null;
    }

    // ── Utilidades de formato ─────────────────────────────────────────────────

    private void write(ByteArrayOutputStream buf, byte[] bytes) {
        buf.write(bytes, 0, bytes.length);
    }

    private void writeLine(ByteArrayOutputStream buf, String texto, String enc) throws IOException {
        buf.write(texto.getBytes(Charset.forName(enc)));
        buf.write(LF);
    }

    private String separador(char c, int ancho) {
        return String.valueOf(c).repeat(ancho);
    }

    private String centrar(String texto, int ancho) {
        if (texto.length() >= ancho) return texto;
        int pad = (ancho - texto.length()) / 2;
        return " ".repeat(pad) + texto;
    }

    private String padRight(String s, int n) {
        if (s.length() >= n) return s.substring(0, n);
        return s + " ".repeat(n - s.length());
    }

    private String padLeft(String s, int n) {
        if (s.length() >= n) return s.substring(0, n);
        return " ".repeat(n - s.length()) + s;
    }

    private String truncar(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + "." : s;
    }

    private String formatMonto(java.math.BigDecimal monto) {
        if (monto == null) return "0.00";
        return String.format("%.2f", monto);
    }

    private String labelMetodo(MetodoPago m) {
        return switch (m) {
            case EFECTIVO        -> "Efectivo";
            case TARJETA_CREDITO -> "Tarjeta Credito";
            case TARJETA_DEBITO  -> "Tarjeta Debito";
            case CREDITO         -> "Credito/Apartado";
        };
    }
}
