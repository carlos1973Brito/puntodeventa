package com.pos.service;

import com.pos.model.MovimientoStock;

import java.util.List;

/**
 * Contrato del servicio de movimientos de stock.
 * Permite registrar entradas de mercancía y ajustes manuales de inventario.
 */
public interface StockService {
    void registrarEntrada(Long productoId, int cantidad, String justificacion, Long usuarioId);
    void registrarAjuste(Long productoId, int nuevaCantidad, String justificacion, Long usuarioId);
    List<MovimientoStock> historialPorProducto(Long productoId);
}
