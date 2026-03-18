package com.pos.service.impl;

import com.pos.exception.PagoInsuficienteException;
import com.pos.exception.PosException;
import com.pos.exception.PosRuntimeException;
import com.pos.exception.StockInsuficienteException;
import com.pos.exception.VentaVaciaException;
import com.pos.model.LineaVenta;
import com.pos.model.MovimientoStock;
import com.pos.model.Producto;
import com.pos.model.Usuario;
import com.pos.model.Venta;
import com.pos.model.enums.EstadoVenta;
import com.pos.model.enums.MetodoPago;
import com.pos.model.enums.TipoMovimiento;
import com.pos.repository.MovimientoStockRepository;
import com.pos.repository.ProductoRepository;
import com.pos.repository.UsuarioRepository;
import com.pos.repository.VentaRepository;
import com.pos.service.VentaService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class VentaServiceImpl implements VentaService {

    private static final Logger log = LoggerFactory.getLogger(VentaServiceImpl.class);

    private final VentaRepository ventaRepository;
    private final ProductoRepository productoRepository;
    private final MovimientoStockRepository movimientoStockRepository;
    private final UsuarioRepository usuarioRepository;

    public VentaServiceImpl(VentaRepository ventaRepository,
                            ProductoRepository productoRepository,
                            MovimientoStockRepository movimientoStockRepository,
                            UsuarioRepository usuarioRepository) {
        this.ventaRepository = ventaRepository;
        this.productoRepository = productoRepository;
        this.movimientoStockRepository = movimientoStockRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public Venta iniciarVenta(Long cajeroId) throws PosException {
        Usuario cajero = usuarioRepository.findById(cajeroId)
                .orElseThrow(() -> new PosRuntimeException("Usuario no encontrado con id: " + cajeroId));

        Venta venta = new Venta(cajero, LocalDateTime.now());
        venta.setEstado(EstadoVenta.EN_CURSO);
        return ventaRepository.save(venta);
    }

    @Override
    public Venta agregarLinea(Long ventaId, String codigoBarras, int cantidad) throws PosException {
        Venta venta = obtenerVentaEnCurso(ventaId);

        // Buscar primero por código de barras; si no hay resultado, intentar por nombre exacto o parcial
        log.info("[agregarLinea] Buscando producto con término: '{}'", codigoBarras);

        Producto producto = productoRepository.findByCodigoBarras(codigoBarras)
                .map(p -> {
                    log.info("[agregarLinea] Encontrado por código de barras: id={} nombre='{}'", p.getId(), p.getNombre());
                    return p;
                })
                .orElseGet(() -> {
                    log.info("[agregarLinea] No encontrado por código de barras, buscando por nombre...");
                    List<Producto> porNombre = productoRepository.findByNombreContainingOrCategoria(codigoBarras);
                    log.info("[agregarLinea] Resultados por nombre ({}): {}", porNombre.size(),
                            porNombre.stream().map(p -> "id=" + p.getId() + " '" + p.getNombre() + "'").toList());
                    return porNombre.stream()
                            .filter(p -> p.getNombre().equalsIgnoreCase(codigoBarras))
                            .findFirst()
                            .orElse(porNombre.isEmpty() ? null : porNombre.get(0));
                });

        if (producto == null) {
            log.warn("[agregarLinea] Producto no encontrado para término: '{}'", codigoBarras);
            throw new PosRuntimeException("Producto no encontrado: " + codigoBarras);
        }
        log.info("[agregarLinea] Producto seleccionado: id={} nombre='{}' stock={}", producto.getId(), producto.getNombre(), producto.getStockActual());

        if (producto.getStockActual() < cantidad) {
            throw new StockInsuficienteException(producto.getStockActual(), cantidad);
        }

        LineaVenta linea = new LineaVenta(producto, cantidad);
        venta.agregarLinea(linea);
        recalcularTotal(venta);

        return ventaRepository.save(venta);
    }

    @Override
    public Venta agregarLineaPorId(Long ventaId, Long productoId, int cantidad) throws PosException {
        Venta venta = obtenerVentaEnCurso(ventaId);

        log.info("[agregarLineaPorId] Buscando producto por id={}", productoId);
        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new PosRuntimeException("Producto no encontrado con id: " + productoId));

        log.info("[agregarLineaPorId] Producto encontrado: nombre='{}' stock={}", producto.getNombre(), producto.getStockActual());

        if (producto.getStockActual() < cantidad) {
            throw new StockInsuficienteException(producto.getStockActual(), cantidad);
        }

        LineaVenta linea = new LineaVenta(producto, cantidad);
        venta.agregarLinea(linea);
        recalcularTotal(venta);

        return ventaRepository.save(venta);
    }

    @Override
    public Venta modificarCantidadLinea(Long ventaId, Long lineaId, int nuevaCantidad) throws PosException {
        Venta venta = obtenerVentaEnCurso(ventaId);

        LineaVenta linea = venta.getLineas().stream()
                .filter(l -> l.getId().equals(lineaId))
                .findFirst()
                .orElseThrow(() -> new PosRuntimeException(
                        "Línea de venta no encontrada con id: " + lineaId));

        Producto producto = linea.getProducto();
        if (producto.getStockActual() < nuevaCantidad) {
            throw new StockInsuficienteException(producto.getStockActual(), nuevaCantidad);
        }

        linea.setCantidad(nuevaCantidad);
        linea.setSubtotal(linea.getPrecioUnitario().multiply(BigDecimal.valueOf(nuevaCantidad)));
        recalcularTotal(venta);

        return ventaRepository.save(venta);
    }

    @Override
    public Venta eliminarLinea(Long ventaId, Long lineaId) throws PosException {
        Venta venta = obtenerVentaEnCurso(ventaId);

        LineaVenta linea = venta.getLineas().stream()
                .filter(l -> l.getId().equals(lineaId))
                .findFirst()
                .orElseThrow(() -> new PosRuntimeException(
                        "Línea de venta no encontrada con id: " + lineaId));

        venta.eliminarLinea(linea);
        recalcularTotal(venta);

        return ventaRepository.save(venta);
    }

    @Override
    public Venta completarVenta(Long ventaId, MetodoPago metodo, BigDecimal montoRecibido) throws PosException {
        Venta venta = obtenerVentaEnCurso(ventaId);

        if (venta.getLineas().isEmpty()) {
            throw new VentaVaciaException();
        }

        // Validar pago en efectivo
        if (metodo == MetodoPago.EFECTIVO && montoRecibido.compareTo(venta.getTotal()) < 0) {
            throw new PagoInsuficienteException(venta.getTotal(), montoRecibido);
        }

        // Calcular cambio (solo para efectivo)
        BigDecimal cambio = null;
        if (metodo == MetodoPago.EFECTIVO) {
            cambio = montoRecibido.subtract(venta.getTotal());
        }

        LocalDateTime ahora = LocalDateTime.now();
        Usuario cajero = venta.getCajero();

        for (LineaVenta linea : venta.getLineas()) {
            Producto producto = linea.getProducto();
            int stockAnterior = producto.getStockActual();
            int stockNuevo = stockAnterior - linea.getCantidad();
            producto.setStockActual(stockNuevo);
            productoRepository.save(producto);

            MovimientoStock movimiento = new MovimientoStock(
                    producto,
                    cajero,
                    TipoMovimiento.SALIDA,
                    linea.getCantidad(),
                    stockAnterior,
                    stockNuevo,
                    "Venta #" + ventaId,
                    ahora
            );
            movimientoStockRepository.save(movimiento);
        }

        venta.setEstado(EstadoVenta.COMPLETADA);
        venta.setMetodoPago(metodo);
        venta.setMontoRecibido(montoRecibido);
        venta.setCambio(cambio);

        return ventaRepository.save(venta);
    }

    @Override
    public void cancelarVenta(Long ventaId) throws PosException {
        Venta venta = obtenerVentaEnCurso(ventaId);
        venta.setEstado(EstadoVenta.CANCELADA);
        ventaRepository.save(venta);
    }

    @Override
    public Venta completarVentaCredito(Long ventaId, BigDecimal anticipo, String nombreCliente) throws PosException {
        Venta venta = obtenerVentaEnCurso(ventaId);

        if (venta.getLineas().isEmpty()) throw new VentaVaciaException();
        if (anticipo == null) anticipo = BigDecimal.ZERO;
        if (anticipo.compareTo(venta.getTotal()) > 0)
            throw new PosRuntimeException("El anticipo no puede superar el total.");

        BigDecimal saldo = venta.getTotal().subtract(anticipo);

        // Descontar stock igual que en venta normal
        LocalDateTime ahora = LocalDateTime.now();
        Usuario cajero = venta.getCajero();
        for (LineaVenta linea : venta.getLineas()) {
            Producto producto = linea.getProducto();
            int stockAnterior = producto.getStockActual();
            int stockNuevo = stockAnterior - linea.getCantidad();
            if (stockNuevo < 0) throw new StockInsuficienteException(stockAnterior, linea.getCantidad());
            producto.setStockActual(stockNuevo);
            productoRepository.save(producto);
            movimientoStockRepository.save(new MovimientoStock(
                    producto, cajero, TipoMovimiento.SALIDA,
                    linea.getCantidad(), stockAnterior, stockNuevo,
                    "Crédito #" + ventaId, ahora));
        }

        venta.setEstado(EstadoVenta.CREDITO);
        venta.setMetodoPago(MetodoPago.CREDITO);
        venta.setMontoRecibido(anticipo);
        venta.setSaldoPendiente(saldo);
        venta.setNombreCliente(nombreCliente);
        return ventaRepository.save(venta);
    }

    @Override
    public Venta abonarCredito(Long ventaId, BigDecimal monto) throws PosException {
        Venta venta = ventaRepository.findById(ventaId)
                .orElseThrow(() -> new PosRuntimeException("Venta no encontrada: " + ventaId));
        if (venta.getEstado() != EstadoVenta.CREDITO)
            throw new PosRuntimeException("La venta #" + ventaId + " no está en estado CREDITO.");
        if (monto.compareTo(BigDecimal.ZERO) <= 0)
            throw new PosRuntimeException("El abono debe ser mayor a cero.");

        BigDecimal nuevoSaldo = venta.getSaldoPendiente().subtract(monto);
        if (nuevoSaldo.compareTo(BigDecimal.ZERO) < 0)
            throw new PosRuntimeException("El abono supera el saldo pendiente de " + venta.getSaldoPendiente());

        venta.setSaldoPendiente(nuevoSaldo);
        venta.setMontoRecibido(venta.getMontoRecibido().add(monto));
        if (nuevoSaldo.compareTo(BigDecimal.ZERO) == 0) {
            venta.setEstado(EstadoVenta.COMPLETADA);
        }
        return ventaRepository.save(venta);
    }

    @Override
    public List<Venta> listarCreditos() {
        return ventaRepository.findCreditos();
    }

    @Override
    public Venta devolverVenta(Long ventaId, String motivo) throws PosException {
        Venta venta = ventaRepository.findById(ventaId)
                .orElseThrow(() -> new PosRuntimeException("Venta no encontrada: " + ventaId));

        if (venta.getEstado() == EstadoVenta.DEVUELTA)
            throw new PosRuntimeException("La venta #" + ventaId + " ya fue devuelta.");
        if (venta.getEstado() == EstadoVenta.EN_CURSO || venta.getEstado() == EstadoVenta.CANCELADA)
            throw new PosRuntimeException("Solo se pueden devolver ventas completadas o a crédito.");

        LocalDateTime ahora = LocalDateTime.now();
        Usuario cajero = venta.getCajero();

        // Reponer stock
        for (LineaVenta linea : venta.getLineas()) {
            Producto producto = linea.getProducto();
            int stockAnterior = producto.getStockActual();
            int stockNuevo = stockAnterior + linea.getCantidad();
            producto.setStockActual(stockNuevo);
            productoRepository.save(producto);
            movimientoStockRepository.save(new MovimientoStock(
                    producto, cajero, TipoMovimiento.ENTRADA,
                    linea.getCantidad(), stockAnterior, stockNuevo,
                    "Devolución venta #" + ventaId + ": " + motivo, ahora));
        }

        venta.setEstado(EstadoVenta.DEVUELTA);
        venta.setMotivoDevolucion(motivo);
        venta.setFechaDevolucion(ahora);
        return ventaRepository.save(venta);
    }

    // --- Métodos auxiliares ---

    private Venta obtenerVentaEnCurso(Long ventaId) {
        Venta venta = ventaRepository.findById(ventaId)
                .orElseThrow(() -> new PosRuntimeException("Venta no encontrada con id: " + ventaId));

        if (venta.getEstado() != EstadoVenta.EN_CURSO) {
            throw new PosRuntimeException(
                    "La venta #" + ventaId + " no está en curso (estado: " + venta.getEstado() + ")");
        }
        return venta;
    }

    private void recalcularTotal(Venta venta) {
        BigDecimal total = venta.getLineas().stream()
                .map(LineaVenta::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        venta.setTotal(total);
    }
}
