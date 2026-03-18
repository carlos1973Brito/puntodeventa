package com.pos.repository.impl;

import com.pos.model.MovimientoStock;
import com.pos.repository.MovimientoStockRepository;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

import java.time.LocalDateTime;
import java.util.List;

public class MovimientoStockRepositoryImpl implements MovimientoStockRepository {

    private final SessionFactory sessionFactory;

    public MovimientoStockRepositoryImpl(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    public MovimientoStock save(MovimientoStock m) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            MovimientoStock saved = (MovimientoStock) session.merge(m);
            tx.commit();
            return saved;
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            throw new RuntimeException("Error al guardar movimiento de stock", e);
        }
    }

    @Override
    public List<MovimientoStock> findByProductoId(Long productoId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                    "SELECT m FROM MovimientoStock m " +
                    "LEFT JOIN FETCH m.producto " +
                    "LEFT JOIN FETCH m.usuario " +
                    "WHERE m.producto.id = :productoId ORDER BY m.fecha DESC",
                    MovimientoStock.class)
                    .setParameter("productoId", productoId)
                    .list();
        } catch (Exception e) {
            throw new RuntimeException("Error al buscar movimientos por producto", e);
        }
    }

    @Override
    public List<MovimientoStock> findByFechaBetween(LocalDateTime inicio, LocalDateTime fin) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                    "SELECT m FROM MovimientoStock m " +
                    "LEFT JOIN FETCH m.producto " +
                    "LEFT JOIN FETCH m.usuario " +
                    "WHERE m.fecha BETWEEN :inicio AND :fin ORDER BY m.fecha DESC",
                    MovimientoStock.class)
                    .setParameter("inicio", inicio)
                    .setParameter("fin", fin)
                    .list();
        } catch (Exception e) {
            throw new RuntimeException("Error al buscar movimientos por fecha", e);
        }
    }

    @Override
    public List<MovimientoStock> findAll() {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                    "SELECT m FROM MovimientoStock m " +
                    "LEFT JOIN FETCH m.producto " +
                    "LEFT JOIN FETCH m.usuario " +
                    "ORDER BY m.fecha DESC",
                    MovimientoStock.class)
                    .list();
        } catch (Exception e) {
            throw new RuntimeException("Error al listar movimientos de stock", e);
        }
    }
}
