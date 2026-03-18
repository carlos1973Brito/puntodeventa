package com.pos.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/**
 * Lee y escribe la configuración del negocio e impresora en config.properties.
 * Escribe en el directorio de datos del usuario (~/.PuntoDeVenta/config.properties)
 * para no modificar el JAR empaquetado.
 */
public class ConfiguracionService {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracionService.class);

    // Ruta del archivo de configuración editable (fuera del JAR)
    private static final Path CONFIG_PATH = Paths.get(
            System.getProperty("user.home"), "PuntoDeVenta", "config.properties");

    // Claves
    public static final String PRINTER_NAME      = "ticket.printer.name";
    public static final String PRINTER_WIDTH      = "ticket.printer.width";
    public static final String NEGOCIO_NOMBRE     = "ticket.negocio.nombre";
    public static final String NEGOCIO_DIRECCION  = "ticket.negocio.direccion";
    public static final String NEGOCIO_TELEFONO   = "ticket.negocio.telefono";
    public static final String NEGOCIO_MENSAJE    = "ticket.negocio.mensaje";

    private final Properties props = new Properties();

    public ConfiguracionService() {
        cargar();
    }

    public String get(String clave, String defecto) {
        return props.getProperty(clave, defecto);
    }

    public void set(String clave, String valor) {
        props.setProperty(clave, valor);
    }

    public void guardar() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (OutputStream os = Files.newOutputStream(CONFIG_PATH)) {
                props.store(os, "Configuración Sistema POS");
            }
            log.info("Configuración guardada en {}", CONFIG_PATH);
        } catch (IOException e) {
            log.error("Error al guardar configuración: {}", e.getMessage(), e);
        }
    }

    private void cargar() {
        // 1. Cargar defaults del classpath
        try (InputStream is = getClass().getResourceAsStream("/config.properties")) {
            if (is != null) props.load(is);
        } catch (IOException e) {
            log.warn("No se pudo leer config.properties del classpath");
        }
        // 2. Sobreescribir con el archivo editable del usuario (si existe)
        if (Files.exists(CONFIG_PATH)) {
            try (InputStream is = Files.newInputStream(CONFIG_PATH)) {
                props.load(is);
                log.info("Configuración cargada desde {}", CONFIG_PATH);
            } catch (IOException e) {
                log.warn("No se pudo leer {}: {}", CONFIG_PATH, e.getMessage());
            }
        }
    }
}
