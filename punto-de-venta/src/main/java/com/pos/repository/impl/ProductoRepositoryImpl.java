package com.pos.repository.impl;

import com.pos.model.Producto;
import com.pos.repository.ProductoRepository;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

import java.util.List;
import java.util.Optional;

public class ProductoRepositoryImpl implements ProductoRepository {

    private final SessionFactory sessionFactory;

    public ProductoRepositoryImpl(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    public Producto save(Producto p) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            Producto saved = (Producto) session.merge(p);
            tx.commit();
            return saved;
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            throw new RuntimeException("Error al guardar producto", e);
        }
    }

    @Override
    public Optional<Producto> findById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return Optional.ofNullable(session.get(Producto.class, id));
        } catch (Exception e) {
            throw new RuntimeException("Error al buscar producto por id", e);
        }
    }

    @Override
    public Optional<Producto> findByCodigoBarras(String codigo) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                    "FROM Producto p WHERE p.codigoBarras = :codigo", Producto.class)
                    .setParameter("codigo", codigo)
                    .uniqueResultOptional();
        } catch (Exception e) {
            throw new RuntimeException("Error al buscar producto por código de barras", e);
        }
    }

    @Override
    public List<Producto> findByNombreContainingOrCategoria(String termino) {
        try (Session session = sessionFactory.openSession()) {
            String like = "%" + termino.toLowerCase() + "%";
            // LEFT JOIN para incluir productos sin categoría; condición de categoría solo si no es null
            return session.createQuery(
                    "FROM Producto p LEFT JOIN FETCH p.categoria c WHERE p.activo = true " +
                    "AND (LOWER(p.nombre) LIKE :like OR (c IS NOT NULL AND LOWER(c.nombre) LIKE :like))",
                    Producto.class)
                    .setParameter("like", like)
                    .list();
        } catch (Exception e) {
            throw new RuntimeException("Error al buscar productos por nombre o categoría", e);
        }
    }

    @Override
    public List<Producto> findAllActivos() {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                    "FROM Producto p WHERE p.activo = true", Producto.class)
                    .list();
        } catch (Exception e) {
            throw new RuntimeException("Error al listar productos activos", e);
        }
    }

    @Override
    public List<Producto> findBajoStockMinimo() {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                    "FROM Producto p WHERE p.activo = true AND p.stockActual <= p.stockMinimo",
                    Producto.class)
                    .list();
        } catch (Exception e) {
            throw new RuntimeException("Error al listar productos bajo stock mínimo", e);
        }
    }
}
