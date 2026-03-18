package com.pos.service.impl;

import com.pos.exception.PosRuntimeException;
import com.pos.model.MovimientoStock;
import com.pos.model.Producto;
import com.pos.model.Usuario;
import com.pos.model.enums.TipoMovimiento;
import com.pos.repository.MovimientoStockRepository;
import com.pos.repository.ProductoRepository;
import com.pos.repository.UsuarioRepository;
import com.pos.service.StockService;

import java.time.LocalDateTime;
import java.util.List;

public class StockServiceImpl implements StockService {

    private final ProductoRepository productoRepository;
    private final MovimientoStockRepository movimientoStockRepository;
    private final UsuarioRepository usuarioRepository;

    public StockServiceImpl(ProductoRepository productoRepository,
                            MovimientoStockRepository movimientoStockRepository,
                            UsuarioRepository usuarioRepository) {
        this.productoRepository = productoRepository;
        this.movimientoStockRepository = movimientoStockRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public void registrarEntrada(Long productoId, int cantidad, String justificacion, Long usuarioId) {
        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new PosRuntimeException("Producto no encontrado con id: " + productoId));
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new PosRuntimeException("Usuario no encontrado con id: " + usuarioId));

        int stockAnterior = producto.getStockActual();
        int stockNuevo = stockAnterior + cantidad;

        producto.setStockActual(stockNuevo);
        productoRepository.save(producto);

        MovimientoStock movimiento = new MovimientoStock(
                producto, usuario, TipoMovimiento.ENTRADA,
                cantidad, stockAnterior, stockNuevo,
                justificacion, LocalDateTime.now());
        movimientoStockRepository.save(movimiento);
    }

    @Override
    public void registrarAjuste(Long productoId, int nuevaCantidad, String justificacion, Long usuarioId) {
        if (justificacion == null || justificacion.isBlank()) {
            throw new IllegalArgumentException("La justificación es obligatoria para un ajuste de inventario");
        }

        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new PosRuntimeException("Producto no encontrado con id: " + productoId));
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new PosRuntimeException("Usuario no encontrado con id: " + usuarioId));

        int stockAnterior = producto.getStockActual();
        int stockNuevo = nuevaCantidad;

        producto.setStockActual(stockNuevo);
        productoRepository.save(producto);

        MovimientoStock movimiento = new MovimientoStock(
                producto, usuario, TipoMovimiento.AJUSTE,
                Math.abs(stockNuevo - stockAnterior), stockAnterior, stockNuevo,
                justificacion, LocalDateTime.now());
        movimientoStockRepository.save(movimiento);
    }

    @Override
    public List<MovimientoStock> historialPorProducto(Long productoId) {
        return movimientoStockRepository.findByProductoId(productoId);
    }
}
