package com.pos.repository.impl;

import com.pos.model.Venta;
import com.pos.model.enums.EstadoVenta;
import com.pos.model.enums.MetodoPago;
import com.pos.repository.VentaRepository;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class VentaRepositoryImpl implements VentaRepository {

    private final SessionFactory sessionFactory;

    public VentaRepositoryImpl(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    public Venta save(Venta v) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            Venta saved = (Venta) session.merge(v);
            tx.commit();
            // Recargar con JOIN FETCH para evitar LazyInitializationException fuera de sesión
            return session.createQuery(
                    "SELECT v FROM Venta v " +
                    "LEFT JOIN FETCH v.cajero " +
                    "LEFT JOIN FETCH v.lineas l " +
                    "LEFT JOIN FETCH l.producto " +
                    "WHERE v.id = :id",
                    Venta.class)
                    .setParameter("id", saved.getId())
                    .uniqueResult();
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            throw new RuntimeException("Error al guardar venta", e);
        }
    }

    @Override
    public Optional<Venta> findById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            Venta venta = session.createQuery(
                    "SELECT v FROM Venta v " +
                    "LEFT JOIN FETCH v.cajero " +
                    "LEFT JOIN FETCH v.lineas l " +
                    "LEFT JOIN FETCH l.producto " +
                    "WHERE v.id = :id",
                    Venta.class)
                    .setParameter("id", id)
                    .uniqueResult();
            return Optional.ofNullable(venta);
        } catch (Exception e) {
            throw new RuntimeException("Error al buscar venta por id", e);
        }
    }

    @Override
    public List<Venta> findByFechaBetween(LocalDateTime desde, LocalDateTime hasta) {
        try (Session session = sessionFactory.openSession()) {
            // JOIN FETCH lineas y producto para evitar LazyInitializationException fuera de sesión
            return session.createQuery(
                    "SELECT DISTINCT v FROM Venta v " +
                    "LEFT JOIN FETCH v.cajero " +
                    "LEFT JOIN FETCH v.lineas l " +
                    "LEFT JOIN FETCH l.producto p " +
                    "LEFT JOIN FETCH p.categoria " +
                    "WHERE v.estado = :estado " +
                    "AND v.fecha >= :desde AND v.fecha <= :hasta",
                    Venta.class)
                    .setParameter("estado", EstadoVenta.COMPLETADA)
                    .setParameter("desde", desde)
                    .setParameter("hasta", hasta)
                    .list();
        } catch (Exception e) {
            throw new RuntimeException("Error al buscar ventas por rango de fechas", e);
        }
    }

    @Override
    public Map<MetodoPago, BigDecimal> sumByMetodoPago(LocalDate desde, LocalDate hasta) {
        try (Session session = sessionFactory.openSession()) {
            LocalDateTime desdeDateTime = desde.atStartOfDay();
            LocalDateTime hastaDateTime = hasta.plusDays(1).atStartOfDay();

            List<Object[]> rows = session.createQuery(
                    "SELECT v.metodoPago, SUM(v.total) FROM Venta v " +
                    "WHERE v.estado = :estado " +
                    "AND v.fecha >= :desde AND v.fecha < :hasta " +
                    "GROUP BY v.metodoPago",
                    Object[].class)
                    .setParameter("estado", EstadoVenta.COMPLETADA)
                    .setParameter("desde", desdeDateTime)
                    .setParameter("hasta", hastaDateTime)
                    .list();

            Map<MetodoPago, BigDecimal> resultado = new EnumMap<>(MetodoPago.class);
            for (Object[] row : rows) {
                MetodoPago metodo = (MetodoPago) row[0];
                BigDecimal suma = (BigDecimal) row[1];
                if (metodo != null) {
                    resultado.put(metodo, suma);
                }
            }
            return resultado;
        } catch (Exception e) {
            throw new RuntimeException("Error al sumar ventas por método de pago", e);
        }
    }

    @Override
    public List<Venta> findCompletadasYCredito(LocalDateTime desde, LocalDateTime hasta) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                    "SELECT DISTINCT v FROM Venta v " +
                    "LEFT JOIN FETCH v.cajero " +
                    "LEFT JOIN FETCH v.lineas l " +
                    "LEFT JOIN FETCH l.producto p " +
                    "LEFT JOIN FETCH p.categoria " +
                    "WHERE v.estado IN (:estados) " +
                    "AND v.fecha >= :desde AND v.fecha <= :hasta",
                    Venta.class)
                    .setParameter("estados", List.of(EstadoVenta.COMPLETADA, EstadoVenta.CREDITO))
                    .setParameter("desde", desde)
                    .setParameter("hasta", hasta)
                    .list();
        } catch (Exception e) {
            throw new RuntimeException("Error al buscar ventas completadas y crédito", e);
        }
    }

    @Override
    public List<Venta> findCreditos() {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                    "SELECT DISTINCT v FROM Venta v " +
                    "LEFT JOIN FETCH v.cajero " +
                    "LEFT JOIN FETCH v.lineas l " +
                    "LEFT JOIN FETCH l.producto " +
                    "WHERE v.estado = :estado AND v.saldoPendiente > 0 " +
                    "ORDER BY v.fecha DESC",
                    Venta.class)
                    .setParameter("estado", EstadoVenta.CREDITO)
                    .list();
        } catch (Exception e) {
            throw new RuntimeException("Error al buscar créditos pendientes", e);
        }
    }

    @Override
    public List<Venta> findByEstado(EstadoVenta estado) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                    "SELECT DISTINCT v FROM Venta v " +
                    "LEFT JOIN FETCH v.cajero " +
                    "LEFT JOIN FETCH v.lineas l " +
                    "LEFT JOIN FETCH l.producto " +
                    "WHERE v.estado = :estado ORDER BY v.fecha DESC",
                    Venta.class)
                    .setParameter("estado", estado)
                    .list();
        } catch (Exception e) {
            throw new RuntimeException("Error al buscar ventas por estado", e);
        }
    }
}
