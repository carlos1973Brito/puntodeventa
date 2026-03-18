package com.pos.repository;

import com.pos.model.MovimientoStock;

import java.time.LocalDateTime;
import java.util.List;

public interface MovimientoStockRepository {
    MovimientoStock save(MovimientoStock m);
    List<MovimientoStock> findByProductoId(Long productoId);
    List<MovimientoStock> findByFechaBetween(LocalDateTime inicio, LocalDateTime fin);
    List<MovimientoStock> findAll();
}
