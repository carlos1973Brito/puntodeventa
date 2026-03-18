package com.pos.repository;

import com.pos.model.Producto;

import java.util.List;
import java.util.Optional;

/**
 * Contrato de acceso a datos para {@link com.pos.model.Producto}.
 *
 * <p>Incluye búsqueda por código de barras (exacta) y por nombre/categoría
 * (parcial, para el autocompletado del POS).
 */
public interface ProductoRepository {
    Producto save(Producto p);
    Optional<Producto> findById(Long id);
    Optional<Producto> findByCodigoBarras(String codigo);
    List<Producto> findByNombreContainingOrCategoria(String termino);
    List<Producto> findAllActivos();
    List<Producto> findBajoStockMinimo();
}
