package com.pos.service;

import com.pos.exception.PosException;
import com.pos.model.Venta;
import com.pos.model.enums.MetodoPago;

import java.math.BigDecimal;
import java.util.List;

public interface VentaService {
    Venta iniciarVenta(Long cajeroId) throws PosException;
    Venta agregarLinea(Long ventaId, String codigoBarras, int cantidad) throws PosException;
    Venta agregarLineaPorId(Long ventaId, Long productoId, int cantidad) throws PosException;
    Venta modificarCantidadLinea(Long ventaId, Long lineaId, int nuevaCantidad) throws PosException;
    Venta eliminarLinea(Long ventaId, Long lineaId) throws PosException;
    Venta completarVenta(Long ventaId, MetodoPago metodo, BigDecimal montoRecibido) throws PosException;
    void cancelarVenta(Long ventaId) throws PosException;

    // Crédito / apartado
    Venta completarVentaCredito(Long ventaId, BigDecimal anticipo, String nombreCliente) throws PosException;
    Venta abonarCredito(Long ventaId, BigDecimal monto) throws PosException;
    List<Venta> listarCreditos();

    // Devoluciones
    Venta devolverVenta(Long ventaId, String motivo) throws PosException;
}
