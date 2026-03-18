package com.pos.service;

import com.pos.dto.ProductoDTO;
import com.pos.exception.PosException;
import com.pos.model.Producto;

import java.util.List;
import java.util.Optional;

public interface ProductoService {
    Producto crear(ProductoDTO dto) throws PosException;
    Producto actualizar(Long id, ProductoDTO dto) throws PosException;
    void desactivar(Long id) throws PosException;
    Optional<Producto> buscarPorCodigo(String codigoBarras);
    List<Producto> buscarPorNombreOCategoria(String termino);
    List<Producto> listarActivos();
    List<Producto> listarBajoStockMinimo();
}
