package com.pos.service.impl;

import com.pos.dto.ProductoDTO;
import com.pos.exception.CodigoBarrasDuplicadoException;
import com.pos.exception.PosException;
import com.pos.exception.PosRuntimeException;
import com.pos.model.Categoria;
import com.pos.model.Producto;
import com.pos.repository.ProductoRepository;
import com.pos.service.ProductoService;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.util.List;
import java.util.Optional;

public class ProductoServiceImpl implements ProductoService {

    private final ProductoRepository productoRepository;
    private final SessionFactory sessionFactory;

    public ProductoServiceImpl(ProductoRepository productoRepository, SessionFactory sessionFactory) {
        this.productoRepository = productoRepository;
        this.sessionFactory = sessionFactory;
    }

    @Override
    public Producto crear(ProductoDTO dto) throws PosException {
        // Validar unicidad del código de barras
        if (dto.codigoBarras() != null && !dto.codigoBarras().isBlank()) {
            Optional<Producto> existente = productoRepository.findByCodigoBarras(dto.codigoBarras());
            if (existente.isPresent()) {
                throw new CodigoBarrasDuplicadoException(dto.codigoBarras());
            }
        }

        Categoria categoria = resolverCategoria(dto.categoriaId());

        Producto producto = new Producto();
        producto.setNombre(dto.nombre());
        producto.setDescripcion(dto.descripcion());
        producto.setCodigoBarras(dto.codigoBarras());
        producto.setPrecioVenta(dto.precioVenta());
        producto.setPrecioCosto(dto.precioCosto());
        producto.setStockActual(dto.stockInicial());
        producto.setStockMinimo(dto.stockMinimo());
        producto.setCategoria(categoria);
        producto.setActivo(true);

        return productoRepository.save(producto);
    }

    @Override
    public Producto actualizar(Long id, ProductoDTO dto) throws PosException {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new PosRuntimeException("Producto no encontrado con id: " + id));

        // Verificar código de barras si cambió
        String nuevoCodigo = dto.codigoBarras();
        if (nuevoCodigo != null && !nuevoCodigo.isBlank()
                && !nuevoCodigo.equals(producto.getCodigoBarras())) {
            Optional<Producto> existente = productoRepository.findByCodigoBarras(nuevoCodigo);
            if (existente.isPresent()) {
                throw new CodigoBarrasDuplicadoException(nuevoCodigo);
            }
        }

        Categoria categoria = resolverCategoria(dto.categoriaId());

        producto.setNombre(dto.nombre());
        producto.setDescripcion(dto.descripcion());
        producto.setCodigoBarras(nuevoCodigo);
        producto.setPrecioVenta(dto.precioVenta());
        producto.setPrecioCosto(dto.precioCosto());
        producto.setStockActual(dto.stockInicial());
        producto.setStockMinimo(dto.stockMinimo());
        producto.setCategoria(categoria);

        return productoRepository.save(producto);
    }

    @Override
    public void desactivar(Long id) throws PosException {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new PosRuntimeException("Producto no encontrado con id: " + id));
        producto.setActivo(false);
        productoRepository.save(producto);
    }

    @Override
    public Optional<Producto> buscarPorCodigo(String codigoBarras) {
        return productoRepository.findByCodigoBarras(codigoBarras);
    }

    @Override
    public List<Producto> buscarPorNombreOCategoria(String termino) {
        return productoRepository.findByNombreContainingOrCategoria(termino);
    }

    @Override
    public List<Producto> listarActivos() {
        return productoRepository.findAllActivos();
    }

    @Override
    public List<Producto> listarBajoStockMinimo() {
        return productoRepository.findBajoStockMinimo();
    }

    // Busca la Categoria por id usando la SessionFactory; retorna null si categoriaId es null
    private Categoria resolverCategoria(Long categoriaId) {
        if (categoriaId == null) {
            return null;
        }
        try (Session session = sessionFactory.openSession()) {
            Categoria categoria = session.get(Categoria.class, categoriaId);
            if (categoria == null) {
                throw new PosRuntimeException("Categoría no encontrada con id: " + categoriaId);
            }
            return categoria;
        } catch (PosRuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new PosRuntimeException("Error al buscar categoría", e);
        }
    }
}
