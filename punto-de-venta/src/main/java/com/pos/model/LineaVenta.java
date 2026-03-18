package com.pos.model;

import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * Un renglón dentro de una venta: producto, cantidad y precio al momento de la venta.
 *
 * <p>Los campos {@code nombreProducto}, {@code precioUnitario} y {@code precioCosto}
 * se copian del producto en el momento de la venta para preservar el histórico
 * aunque el producto cambie de precio o sea desactivado posteriormente.
 */
@Entity
@Table(name = "linea_venta")
public class LineaVenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venta_id", nullable = false)
    private Venta venta;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(name = "nombre_producto", nullable = false, length = 200)
    private String nombreProducto;

    @Column(name = "precio_unitario", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioUnitario;

    @Column(name = "precio_costo", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioCosto;

    @Column(nullable = false)
    private int cantidad;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "margen_porcentaje", precision = 10, scale = 2)
    private BigDecimal margenPorcentaje;

    // Constructor requerido por Hibernate
    public LineaVenta() {}

    public LineaVenta(Producto producto, int cantidad) {
        this.producto = producto;
        this.nombreProducto = producto.getNombre();
        this.precioUnitario = producto.getPrecioVenta();
        this.precioCosto = producto.getPrecioCosto();
        this.cantidad = cantidad;
        this.subtotal = producto.getPrecioVenta().multiply(BigDecimal.valueOf(cantidad));
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Venta getVenta() { return venta; }
    public void setVenta(Venta venta) { this.venta = venta; }

    public Producto getProducto() { return producto; }
    public void setProducto(Producto producto) { this.producto = producto; }

    public String getNombreProducto() { return nombreProducto; }
    public void setNombreProducto(String nombreProducto) { this.nombreProducto = nombreProducto; }

    public BigDecimal getPrecioUnitario() { return precioUnitario; }
    public void setPrecioUnitario(BigDecimal precioUnitario) { this.precioUnitario = precioUnitario; }

    public BigDecimal getPrecioCosto() { return precioCosto; }
    public void setPrecioCosto(BigDecimal precioCosto) { this.precioCosto = precioCosto; }

    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getMargenPorcentaje() { return margenPorcentaje; }
    public void setMargenPorcentaje(BigDecimal margenPorcentaje) { this.margenPorcentaje = margenPorcentaje; }
}
