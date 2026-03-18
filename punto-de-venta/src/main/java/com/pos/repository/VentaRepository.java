package com.pos.repository;

import com.pos.model.Venta;
import com.pos.model.enums.MetodoPago;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Contrato de acceso a datos para {@link com.pos.model.Venta}.
 *
 * <p>Incluye consultas especializadas para historial, corte de caja,
 * créditos pendientes y reportes por método de pago.
 */
public interface VentaRepository {
    Venta save(Venta v);
    Optional<Venta> findById(Long id);
    List<Venta> findByFechaBetween(LocalDateTime desde, LocalDateTime hasta);
    Map<MetodoPago, BigDecimal> sumByMetodoPago(LocalDate desde, LocalDate hasta);
    List<Venta> findCompletadasYCredito(LocalDateTime desde, LocalDateTime hasta);
    List<Venta> findCreditos();
    List<Venta> findByEstado(com.pos.model.enums.EstadoVenta estado);
}
