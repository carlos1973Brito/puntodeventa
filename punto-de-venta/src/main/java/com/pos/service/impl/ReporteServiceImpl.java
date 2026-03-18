package com.pos.service.impl;

import com.pos.dto.ProductoVendidoDTO;
import com.pos.dto.ReporteInventarioDTO;
import com.pos.dto.ReporteVentasDTO;
import com.pos.model.LineaVenta;
import com.pos.model.Producto;
import com.pos.model.Venta;
import com.pos.model.enums.EstadoVenta;
import com.pos.model.enums.MetodoPago;
import com.pos.repository.ProductoRepository;
import com.pos.repository.VentaRepository;
import com.pos.service.ReporteService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class ReporteServiceImpl implements ReporteService {

    private final VentaRepository ventaRepository;
    private final ProductoRepository productoRepository;

    public ReporteServiceImpl(VentaRepository ventaRepository, ProductoRepository productoRepository) {
        this.ventaRepository = ventaRepository;
        this.productoRepository = productoRepository;
    }

    @Override
    public ReporteVentasDTO generarReporteVentas(LocalDate desde, LocalDate hasta) {
        // Obtener ventas COMPLETADAS en el rango
        List<Venta> todasVentas = ventaRepository.findByFechaBetween(
                desde.atStartOfDay(),
                hasta.plusDays(1).atStartOfDay()
        );

        List<Venta> ventas = todasVentas.stream()
                .filter(v -> EstadoVenta.COMPLETADA.equals(v.getEstado()))
                .collect(Collectors.toList());

        // Total ventas y total recaudado
        int totalVentas = ventas.size();
        BigDecimal totalRecaudado = ventas.stream()
                .map(Venta::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Desglose por método de pago
        Map<MetodoPago, BigDecimal> porMetodoPago = ventaRepository.sumByMetodoPago(desde, hasta);

        // Productos más vendidos: agrupar líneas por nombreProducto
        Map<String, int[]> acumuladorCantidad = new LinkedHashMap<>();
        Map<String, BigDecimal> acumuladorTotal = new LinkedHashMap<>();

        for (Venta venta : ventas) {
            for (LineaVenta linea : venta.getLineas()) {
                String nombre = linea.getNombreProducto();
                acumuladorCantidad.merge(nombre, new int[]{linea.getCantidad()},
                        (a, b) -> new int[]{a[0] + b[0]});
                acumuladorTotal.merge(nombre, linea.getSubtotal(), BigDecimal::add);
            }
        }

        List<ProductoVendidoDTO> masVendidos = acumuladorCantidad.entrySet().stream()
                .map(e -> new ProductoVendidoDTO(
                        e.getKey(),
                        e.getValue()[0],
                        acumuladorTotal.getOrDefault(e.getKey(), BigDecimal.ZERO)
                ))
                .sorted(Comparator.comparingInt(ProductoVendidoDTO::cantidadTotal).reversed())
                .limit(10)
                .collect(Collectors.toList());

        // Agrupado por categoría
        Map<String, BigDecimal> porCategoria = new LinkedHashMap<>();
        for (Venta venta : ventas) {
            for (LineaVenta linea : venta.getLineas()) {
                String categoria = "Sin categoría";
                Producto producto = linea.getProducto();
                if (producto != null && producto.getCategoria() != null) {
                    categoria = producto.getCategoria().getNombre();
                }
                porCategoria.merge(categoria, linea.getSubtotal(), BigDecimal::add);
            }
        }

        return new ReporteVentasDTO(desde, hasta, totalVentas, totalRecaudado,
                porMetodoPago, masVendidos, porCategoria);
    }

    @Override
    public ReporteInventarioDTO generarReporteInventario() {
        List<Producto> productosActivos = productoRepository.findAllActivos();

        BigDecimal valorTotalInventario = productosActivos.stream()
                .map(p -> p.getPrecioCosto().multiply(BigDecimal.valueOf(p.getStockActual())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Producto> productosBajoStock = productoRepository.findBajoStockMinimo();

        return new ReporteInventarioDTO(productosActivos, valorTotalInventario, productosBajoStock);
    }
}
