package com.pos.repository;

import com.pos.model.Producto;

import java.util.List;
import java.util.Optional;

public interface ProductoRepository {
    Producto save(Producto p);
    Optional<Producto> findById(Long id);
    Optional<Producto> findByCodigoBarras(String codigo);
    List<Producto> findByNombreContainingOrCategoria(String termino);
    List<Producto> findAllActivos();
    List<Producto> findBajoStockMinimo();
}
