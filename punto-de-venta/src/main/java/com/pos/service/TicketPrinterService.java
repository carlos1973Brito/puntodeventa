package com.pos.service;

import com.pos.model.Venta;

/**
 * Servicio de impresión de tickets en impresoras térmicas ESC/POS.
 */
public interface TicketPrinterService {

    /**
     * Imprime el ticket de una venta en la impresora configurada.
     * @param venta venta completada
     * @throws Exception si no hay impresora configurada o falla la impresión
     */
    void imprimirTicket(Venta venta) throws Exception;

    /**
     * Imprime una prueba de impresión (texto simple).
     * @param nombreImpresora nombre exacto de la impresora en Windows
     * @throws Exception si falla
     */
    void imprimirPrueba(String nombreImpresora) throws Exception;

    /**
     * Lista los nombres de todas las impresoras instaladas en el sistema.
     */
    java.util.List<String> listarImpresoras();
}
