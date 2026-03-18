package com.pos.repository.impl;

import com.pos.model.Usuario;
import com.pos.repository.UsuarioRepository;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

import java.util.List;
import java.util.Optional;

public class UsuarioRepositoryImpl implements UsuarioRepository {

    private final SessionFactory sessionFactory;

    public UsuarioRepositoryImpl(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    public Usuario save(Usuario u) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            Usuario saved = (Usuario) session.merge(u);
            tx.commit();
            return saved;
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            throw new RuntimeException("Error al guardar usuario", e);
        }
    }

    @Override
    public Optional<Usuario> findById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return Optional.ofNullable(session.get(Usuario.class, id));
        } catch (Exception e) {
            throw new RuntimeException("Error al buscar usuario por id", e);
        }
    }

    @Override
    public Optional<Usuario> findByUsername(String username) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                    "FROM Usuario u WHERE u.username = :username", Usuario.class)
                    .setParameter("username", username)
                    .uniqueResultOptional();
        } catch (Exception e) {
            throw new RuntimeException("Error al buscar usuario por username", e);
        }
    }

    @Override
    public List<Usuario> findAllActivos() {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                    "FROM Usuario u WHERE u.activo = true", Usuario.class)
                    .list();
        } catch (Exception e) {
            throw new RuntimeException("Error al listar usuarios activos", e);
        }
    }
}
