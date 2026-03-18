package com.pos.service;

import com.pos.exception.PosException;
import com.pos.exception.PosRuntimeException;
import com.pos.exception.StockInsuficienteException;
import com.pos.exception.VentaVaciaException;
import com.pos.model.LineaVenta;
import com.pos.model.Producto;
import com.pos.model.Usuario;
import com.pos.model.Venta;
import com.pos.model.enums.EstadoVenta;
import com.pos.model.enums.MetodoPago;
import com.pos.repository.MovimientoStockRepository;
import com.pos.repository.ProductoRepository;
import com.pos.repository.UsuarioRepository;
import com.pos.repository.VentaRepository;
import com.pos.service.impl.VentaServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VentaServiceImplTest {

    @Mock VentaRepository ventaRepository;
    @Mock ProductoRepository productoRepository;
    @Mock MovimientoStockRepository movimientoStockRepository;
    @Mock UsuarioRepository usuarioRepository;

    VentaService service;

    @BeforeEach
    void setUp() {
        service = new VentaServiceImpl(ventaRepository, productoRepository,
                movimientoStockRepository, usuarioRepository);
    }

    // --- Helpers ---

    private Usuario cajero(Long id) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setUsername("cajero");
        return u;
    }

    private Producto producto(Long id, String codigo, BigDecimal precio, int stock) {
        Producto p = new Producto();
        p.setId(id);
        p.setNombre("Producto " + id);
        p.setCodigoBarras(codigo);
        p.setPrecioVenta(precio);
        p.setPrecioCosto(BigDecimal.ONE);
        p.setStockActual(stock);
        return p;
    }

    private Venta ventaEnCurso(Long id, Usuario cajero) {
        Venta v = new Venta(cajero, LocalDateTime.now());
        v.setId(id);
        v.setEstado(EstadoVenta.EN_CURSO);
        return v;
    }

    // --- iniciarVenta ---

    @Test
    void iniciarVenta_creaVentaEnCurso() throws PosException {
        Usuario cajero = cajero(1L);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(cajero));
        when(ventaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Venta venta = service.iniciarVenta(1L);

        assertEquals(EstadoVenta.EN_CURSO, venta.getEstado());
        assertEquals(cajero, venta.getCajero());
        assertNotNull(venta.getFecha());
    }

    @Test
    void iniciarVenta_cajeroNoExiste_lanzaExcepcion() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(PosRuntimeException.class, () -> service.iniciarVenta(99L));
    }

    // --- agregarLinea ---

    @Test
    void agregarLinea_calculaSubtotalYTotal() throws PosException {
        Usuario cajero = cajero(1L);
        Venta venta = ventaEnCurso(10L, cajero);
        Producto prod = producto(1L, "ABC123", new BigDecimal("5.00"), 10);

        when(ventaRepository.findById(10L)).thenReturn(Optional.of(venta));
        when(productoRepository.findByCodigoBarras("ABC123")).thenReturn(Optional.of(prod));
        when(ventaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Venta resultado = service.agregarLinea(10L, "ABC123", 3);

        assertEquals(1, resultado.getLineas().size());
        assertEquals(new BigDecimal("15.00"), resultado.getTotal());
        assertEquals(new BigDecimal("15.00"), resultado.getLineas().get(0).getSubtotal());
    }

    @Test
    void agregarLinea_stockInsuficiente_lanzaExcepcion() {
        Usuario cajero = cajero(1L);
        Venta venta = ventaEnCurso(10L, cajero);
        Producto prod = producto(1L, "ABC123", new BigDecimal("5.00"), 2);

        when(ventaRepository.findById(10L)).thenReturn(Optional.of(venta));
        when(productoRepository.findByCodigoBarras("ABC123")).thenReturn(Optional.of(prod));

        StockInsuficienteException ex = assertThrows(StockInsuficienteException.class,
                () -> service.agregarLinea(10L, "ABC123", 5));
        assertEquals(2, ex.getStockDisponible());
        assertEquals(5, ex.getCantidadSolicitada());
    }

    @Test
    void agregarLinea_productoNoExiste_lanzaExcepcion() {
        Usuario cajero = cajero(1L);
        Venta venta = ventaEnCurso(10L, cajero);

        when(ventaRepository.findById(10L)).thenReturn(Optional.of(venta));
        when(productoRepository.findByCodigoBarras("NOEXISTE")).thenReturn(Optional.empty());
        when(productoRepository.findByNombreContainingOrCategoria("NOEXISTE")).thenReturn(List.of());

        assertThrows(PosRuntimeException.class,
                () -> service.agregarLinea(10L, "NOEXISTE", 1));
    }

    @Test
    void agregarLinea_ventaNoEnCurso_lanzaExcepcion() {
        Usuario cajero = cajero(1L);
        Venta venta = ventaEnCurso(10L, cajero);
        venta.setEstado(EstadoVenta.COMPLETADA);

        when(ventaRepository.findById(10L)).thenReturn(Optional.of(venta));

        assertThrows(PosRuntimeException.class,
                () -> service.agregarLinea(10L, "ABC123", 1));
    }

    // --- modificarCantidadLinea ---

    @Test
    void modificarCantidadLinea_actualizaSubtotalYTotal() throws PosException {
        Usuario cajero = cajero(1L);
        Venta venta = ventaEnCurso(10L, cajero);
        Producto prod = producto(1L, "ABC123", new BigDecimal("10.00"), 20);
        LineaVenta linea = new LineaVenta(prod, 2);
        linea.setId(100L);
        venta.agregarLinea(linea);
        venta.setTotal(new BigDecimal("20.00"));

        when(ventaRepository.findById(10L)).thenReturn(Optional.of(venta));
        when(ventaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Venta resultado = service.modificarCantidadLinea(10L, 100L, 5);

        assertEquals(5, resultado.getLineas().get(0).getCantidad());
        assertEquals(new BigDecimal("50.00"), resultado.getLineas().get(0).getSubtotal());
        assertEquals(new BigDecimal("50.00"), resultado.getTotal());
    }

    @Test
    void modificarCantidadLinea_stockInsuficiente_lanzaExcepcion() {
        Usuario cajero = cajero(1L);
        Venta venta = ventaEnCurso(10L, cajero);
        Producto prod = producto(1L, "ABC123", new BigDecimal("10.00"), 3);
        LineaVenta linea = new LineaVenta(prod, 1);
        linea.setId(100L);
        venta.agregarLinea(linea);

        when(ventaRepository.findById(10L)).thenReturn(Optional.of(venta));

        assertThrows(StockInsuficienteException.class,
                () -> service.modificarCantidadLinea(10L, 100L, 10));
    }

    // --- eliminarLinea ---

    @Test
    void eliminarLinea_recalculaTotal() throws PosException {
        Usuario cajero = cajero(1L);
        Venta venta = ventaEnCurso(10L, cajero);
        Producto prod1 = producto(1L, "A", new BigDecimal("10.00"), 10);
        Producto prod2 = producto(2L, "B", new BigDecimal("5.00"), 10);
        LineaVenta linea1 = new LineaVenta(prod1, 2); // subtotal 20
        linea1.setId(100L);
        LineaVenta linea2 = new LineaVenta(prod2, 3); // subtotal 15
        linea2.setId(101L);
        venta.agregarLinea(linea1);
        venta.agregarLinea(linea2);
        venta.setTotal(new BigDecimal("35.00"));

        when(ventaRepository.findById(10L)).thenReturn(Optional.of(venta));
        when(ventaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Venta resultado = service.eliminarLinea(10L, 100L);

        assertEquals(1, resultado.getLineas().size());
        assertEquals(new BigDecimal("15.00"), resultado.getTotal());
    }

    // --- completarVenta ---

    @Test
    void completarVenta_reduceStockYCambiaEstado() throws PosException {
        Usuario cajero = cajero(1L);
        Venta venta = ventaEnCurso(10L, cajero);
        Producto prod = producto(1L, "ABC123", new BigDecimal("10.00"), 10);
        LineaVenta linea = new LineaVenta(prod, 3);
        linea.setId(100L);
        venta.agregarLinea(linea);
        venta.setTotal(new BigDecimal("30.00"));

        when(ventaRepository.findById(10L)).thenReturn(Optional.of(venta));
        when(productoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(movimientoStockRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ventaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Venta resultado = service.completarVenta(10L, MetodoPago.EFECTIVO, new BigDecimal("50.00"));

        assertEquals(EstadoVenta.COMPLETADA, resultado.getEstado());
        assertEquals(MetodoPago.EFECTIVO, resultado.getMetodoPago());
        assertEquals(new BigDecimal("50.00"), resultado.getMontoRecibido());
        assertEquals(7, prod.getStockActual()); // 10 - 3
        verify(movimientoStockRepository, times(1)).save(any());
    }

    @Test
    void completarVenta_ventaVacia_lanzaExcepcion() {
        Usuario cajero = cajero(1L);
        Venta venta = ventaEnCurso(10L, cajero);

        when(ventaRepository.findById(10L)).thenReturn(Optional.of(venta));

        assertThrows(VentaVaciaException.class,
                () -> service.completarVenta(10L, MetodoPago.EFECTIVO, new BigDecimal("0")));
    }

    @Test
    void completarVenta_pagoInsuficiente_lanzaExcepcion() {
        Usuario cajero = cajero(1L);
        Venta venta = ventaEnCurso(10L, cajero);
        Producto prod = producto(1L, "ABC123", new BigDecimal("10.00"), 10);
        LineaVenta linea = new LineaVenta(prod, 3);
        linea.setId(100L);
        venta.agregarLinea(linea);
        venta.setTotal(new BigDecimal("30.00"));

        when(ventaRepository.findById(10L)).thenReturn(Optional.of(venta));

        assertThrows(com.pos.exception.PagoInsuficienteException.class,
                () -> service.completarVenta(10L, MetodoPago.EFECTIVO, new BigDecimal("20.00")));
    }

    @Test
    void completarVenta_efectivo_calculaCambioCorrectamente() throws PosException {
        Usuario cajero = cajero(1L);
        Venta venta = ventaEnCurso(10L, cajero);
        Producto prod = producto(1L, "ABC123", new BigDecimal("10.00"), 10);
        LineaVenta linea = new LineaVenta(prod, 2);
        linea.setId(100L);
        venta.agregarLinea(linea);
        venta.setTotal(new BigDecimal("20.00"));

        when(ventaRepository.findById(10L)).thenReturn(Optional.of(venta));
        when(productoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(movimientoStockRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ventaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Venta resultado = service.completarVenta(10L, MetodoPago.EFECTIVO, new BigDecimal("50.00"));

        assertEquals(new BigDecimal("30.00"), resultado.getCambio());
    }

    @Test
    void completarVenta_tarjeta_cambioEsNull() throws PosException {
        Usuario cajero = cajero(1L);
        Venta venta = ventaEnCurso(10L, cajero);
        Producto prod = producto(1L, "ABC123", new BigDecimal("10.00"), 10);
        LineaVenta linea = new LineaVenta(prod, 2);
        linea.setId(100L);
        venta.agregarLinea(linea);
        venta.setTotal(new BigDecimal("20.00"));

        when(ventaRepository.findById(10L)).thenReturn(Optional.of(venta));
        when(productoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(movimientoStockRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ventaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Venta resultado = service.completarVenta(10L, MetodoPago.TARJETA_CREDITO, new BigDecimal("20.00"));

        assertNull(resultado.getCambio());
        assertEquals(EstadoVenta.COMPLETADA, resultado.getEstado());
    }

    // --- cancelarVenta ---

    @Test
    void cancelarVenta_cambiaEstadoSinTocarStock() throws PosException {
        Usuario cajero = cajero(1L);
        Venta venta = ventaEnCurso(10L, cajero);
        Producto prod = producto(1L, "ABC123", new BigDecimal("10.00"), 10);
        LineaVenta linea = new LineaVenta(prod, 3);
        linea.setId(100L);
        venta.agregarLinea(linea);

        when(ventaRepository.findById(10L)).thenReturn(Optional.of(venta));
        when(ventaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.cancelarVenta(10L);

        assertEquals(EstadoVenta.CANCELADA, venta.getEstado());
        assertEquals(10, prod.getStockActual()); // stock sin cambios
        verify(productoRepository, never()).save(any());
    }

    @Test
    void cancelarVenta_ventaNoEnCurso_lanzaExcepcion() {
        Usuario cajero = cajero(1L);
        Venta venta = ventaEnCurso(10L, cajero);
        venta.setEstado(EstadoVenta.COMPLETADA);

        when(ventaRepository.findById(10L)).thenReturn(Optional.of(venta));

        assertThrows(PosRuntimeException.class, () -> service.cancelarVenta(10L));
    }

    // --- recalcularTotal ---

    @Test
    void recalcularTotal_sumaCorrectaDeSubtotales() throws PosException {
        Usuario cajero = cajero(1L);
        Venta venta = ventaEnCurso(10L, cajero);
        Producto p1 = producto(1L, "A", new BigDecimal("3.50"), 10);
        Producto p2 = producto(2L, "B", new BigDecimal("2.00"), 10);

        when(ventaRepository.findById(10L)).thenReturn(Optional.of(venta));
        when(productoRepository.findByCodigoBarras("A")).thenReturn(Optional.of(p1));
        when(productoRepository.findByCodigoBarras("B")).thenReturn(Optional.of(p2));
        when(ventaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.agregarLinea(10L, "A", 2); // 7.00
        service.agregarLinea(10L, "B", 4); // 8.00

        assertEquals(new BigDecimal("15.00"), venta.getTotal());
    }
}
