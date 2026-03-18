# Plan de Implementación: Sistema de Punto de Venta (POS)

## Visión General

Implementación incremental del sistema POS en Java 17 + JavaFX 21, siguiendo la arquitectura MVC en capas con Hibernate ORM. Cada tarea construye sobre la anterior, terminando con la integración completa de todos los módulos.

## Tareas

- [x] 1. Configurar estructura del proyecto y persistencia base
  - Crear proyecto Maven con dependencias: JavaFX 21, Hibernate 6, SQLite JDBC, H2 (test), JUnit 5, jqwik, Apache POI, OpenPDF, Mockito
  - Definir enums: `Rol`, `MetodoPago`, `EstadoVenta`, `TipoMovimiento`, `DatabaseType`
  - Implementar `DatabaseConfig` con soporte SQLite/PostgreSQL/MySQL vía `config.properties`
  - Crear esquema DDL inicial (tablas: usuario, categoria, producto, movimiento_stock, venta, linea_venta)
  - Implementar `MainApp.java` con inicialización de Hibernate y carga de pantalla de login
  - _Requisitos: 8.2, 8.3, 8.4, 8.5_

- [ ] 2. Implementar capa de dominio: entidades y repositorios
  - [x] 2.1 Crear entidades Hibernate: `Usuario`, `Categoria`, `Producto`, `MovimientoStock`, `Venta`, `LineaVenta`
    - Anotar con `@Entity`, `@Table`, relaciones `@ManyToOne`, `@OneToMany`
    - _Requisitos: 1.1, 2.3, 3.5_
  - [x] 2.2 Implementar repositorios: `ProductoRepository`, `VentaRepository`, `MovimientoStockRepository`, `UsuarioRepository`
    - Implementar todos los métodos de las interfaces definidas en el diseño
    - _Requisitos: 1.1, 1.5, 2.3, 3.5_
  - [ ]* 2.3 Escribir pruebas de integración para repositorios con H2
    - Verificar save/find para cada repositorio
    - _Requisitos: 1.1, 1.2, 2.3_

- [ ] 3. Implementar `ProductoService` y `StockService`
  - [x] 3.1 Implementar `ProductoService`: crear, actualizar, desactivar, buscar por código/nombre/categoría, listar bajo stock mínimo
    - Lanzar `CodigoBarrasDuplicadoException` en código duplicado
    - _Requisitos: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6_
  - [ ]* 3.2 Escribir prueba de propiedad para unicidad de código de barras
    - **Propiedad 6: Unicidad de código de barras**
    - **Valida: Requisito 1.2**
  - [x] 3.3 Implementar `StockService`: registrarEntrada, registrarAjuste, historialPorProducto
    - Registrar `MovimientoStock` con fecha, usuario, tipo y cantidades anterior/nueva
    - _Requisitos: 2.2, 2.3, 2.5_
  - [ ]* 3.4 Escribir prueba de propiedad para consistencia de stock tras venta
    - **Propiedad 1: Consistencia del stock tras una venta**
    - **Valida: Requisito 2.1**
  - [ ]* 3.5 Escribir prueba de propiedad para stock nunca negativo
    - **Propiedad 2: El stock nunca es negativo**
    - **Valida: Requisito 2.4**

- [ ] 4. Checkpoint — Asegurarse de que todas las pruebas pasen, consultar al usuario si hay dudas.

- [ ] 5. Implementar `VentaService` y `PagoService`
  - [x] 5.1 Implementar `VentaService`: iniciarVenta, agregarLinea, modificarCantidadLinea, eliminarLinea, completarVenta, cancelarVenta
    - `agregarLinea`: buscar producto por código de barras, lanzar `StockInsuficienteException` si no hay stock
    - `completarVenta`: reducir stock, registrar `MovimientoStock`, cambiar estado a COMPLETADA
    - `cancelarVenta`: cambiar estado a CANCELADA sin tocar stock
    - Lanzar `VentaVaciaException` si se intenta completar sin líneas
    - _Requisitos: 2.1, 2.4, 3.1, 3.2, 3.3, 3.4, 3.5, 3.7, 3.8_
  - [ ]* 5.2 Escribir prueba de propiedad para cálculo correcto del total
    - **Propiedad 3: Cálculo correcto del total de venta**
    - **Valida: Requisito 3.4**
  - [ ]* 5.3 Escribir prueba de propiedad para idempotencia de cancelación
    - **Propiedad 5: Idempotencia de cancelación de venta**
    - **Valida: Requisito 3.8**
  - [x] 5.4 Implementar lógica de pago en `completarVenta`: calcular cambio para efectivo, lanzar `PagoInsuficienteException` si monto < total
    - _Requisitos: 4.1, 4.2, 4.3, 4.4_
  - [ ]* 5.5 Escribir prueba de propiedad para cálculo del cambio en efectivo
    - **Propiedad 4: Cálculo correcto del cambio en efectivo**
    - **Valida: Requisito 4.2**

- [ ] 6. Implementar `UsuarioService` y autenticación
  - [x] 6.1 Implementar `UsuarioService`: crear, desactivar, cambiarContrasena, autenticar, registrarIntentoFallido, estaBloqueado
    - Hash de contraseña con BCrypt
    - Bloquear cuenta 15 minutos tras 5 intentos fallidos consecutivos
    - Marcar `debecambiarPassword = true` al restablecer contraseña
    - _Requisitos: 7.1, 7.2, 7.4, 7.5, 7.6_
  - [ ]* 6.2 Escribir prueba de propiedad para bloqueo de cuenta
    - **Propiedad 8: Bloqueo de cuenta por intentos fallidos**
    - **Valida: Requisito 7.5**
  - [ ]* 6.3 Escribir pruebas unitarias para control de acceso por rol
    - Verificar que rol Cajero no accede a funciones de administración
    - _Requisitos: 7.2, 7.3_

- [ ] 7. Checkpoint — Asegurarse de que todas las pruebas pasen, consultar al usuario si hay dudas.

- [ ] 8. Implementar `ReporteService`
  - [x] 8.1 Implementar `generarReporteVentas(desde, hasta)`: total ventas, total recaudado, desglose por método de pago, productos más vendidos, agrupado por categoría
    - _Requisitos: 5.1, 5.2, 5.3, 5.5_
  - [ ]* 8.2 Escribir prueba de propiedad para consistencia del reporte de ventas
    - **Propiedad 7: Consistencia del reporte de ventas**
    - **Valida: Requisito 5.1**
  - [x] 8.3 Implementar `generarReporteInventario()`: productos activos con stock, precios, valor total del inventario, sección de bajo stock
    - _Requisitos: 6.1, 6.2, 6.3_
  - [ ]* 8.4 Escribir prueba de propiedad para valor total del inventario
    - **Propiedad 9: Valor total del inventario**
    - **Valida: Requisito 6.2**
  - [x] 8.5 Implementar `exportarPDF(reporte)` con OpenPDF para reportes de ventas e inventario
    - _Requisitos: 5.4, 6.4_

- [ ] 9. Implementar `ExcelImportService`
  - [x] 9.1 Implementar `previsualizarExcel(archivo)` con Apache POI
    - Iterar sobre cada hoja; parsear fecha del nombre de hoja
    - Para cada fila: parsear hora columna 3 en formato 12h AM/PM y combinar con fecha de hoja para construir `LocalDateTime`
    - Validar artículo no vacío, precio numérico, hora con formato correcto
    - Si nombre de hoja no es fecha válida: registrar error y omitir hoja
    - Retornar `ImportPreview` con filas, errores, productosNuevos y ventasARegistrar
    - _Requisitos: 9.1, 9.4, 9.6, 9.7_
  - [x] 9.2 Implementar `importarExcel(archivo)`: crear productos nuevos (stock=100, costo=0) y registrar ventas individuales
    - Deduplicación de artículos por nombre (case-insensitive)
    - _Requisitos: 9.2, 9.3, 9.4, 9.5_
  - [ ]* 9.3 Escribir prueba de propiedad para creación de productos en importación
    - **Propiedad 10: Importación Excel crea productos faltantes con stock 100 y costo 0**
    - **Valida: Requisito 9.2**
  - [ ]* 9.4 Escribir prueba de propiedad para fechahora correcta en ventas importadas
    - **Propiedad 11: Importación Excel registra ventas con fechahora correcta**
    - **Valida: Requisito 9.3**
  - [ ]* 9.5 Escribir prueba de propiedad para tolerancia a filas inválidas
    - **Propiedad 12: Filas inválidas no detienen la importación**
    - **Valida: Requisito 9.4**

- [ ] 10. Checkpoint — Asegurarse de que todas las pruebas pasen, consultar al usuario si hay dudas.

- [x] 11. Implementar vistas JavaFX: Login y navegación principal
  - Crear `login.fxml` + `LoginController`: formulario usuario/contraseña, manejo de `CredencialesInvalidasException` y `UsuarioBloqueadoException`
  - Crear `main.fxml` + `MainController`: menú lateral con navegación por módulo, control de visibilidad según rol
  - _Requisitos: 7.1, 7.2, 7.3_

- [x] 12. Implementar vista de Ventas (POS)
  - Crear `venta.fxml` + `VentaController`
  - Búsqueda de producto por código de barras o nombre, tabla de líneas de venta con subtotales, total en tiempo real
  - Diálogo de pago: selección de método, campo monto recibido, cálculo de cambio
  - Generación y visualización del ticket al completar venta
  - _Requisitos: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 4.1, 4.2, 4.3, 4.4_

- [x] 13. Implementar vistas de Inventario y Productos
  - Crear `producto.fxml` + `ProductoController`: tabla de productos, formulario CRUD, búsqueda, alerta de bajo stock
  - Crear `inventario.fxml` + `InventarioController`: historial de movimientos, formulario de entrada de mercancía, ajuste manual con justificación
  - _Requisitos: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 2.2, 2.3, 2.5_

- [x] 14. Implementar vistas de Reportes e Importación
  - Crear `reporte.fxml` + `ReporteController`: selector de rango de fechas, visualización de reporte de ventas e inventario, botón exportar PDF
  - Crear `importar.fxml` + `ImportarController`: selector de archivo, tabla de previsualización con filas válidas/inválidas resaltadas, botón confirmar importación, resumen de resultado
  - _Requisitos: 5.1, 5.2, 5.3, 5.4, 5.5, 6.1, 6.2, 6.3, 6.4, 9.1, 9.5, 9.6, 9.7_

- [x] 15. Implementar vista de Gestión de Usuarios
  - Crear `usuario.fxml` + `UsuarioController`: tabla de usuarios, formulario crear/editar, desactivar cuenta, restablecer contraseña
  - _Requisitos: 7.4, 7.6_

- [x] 16. Configurar empaquetado nativo con jpackage
  - Configurar plugin `jpackage-maven-plugin` para generar instalador `.exe` para Windows 10/11
  - Incluir JRE embebido y SQLite como dependencia nativa
  - Configurar `config.properties` por defecto con SQLite
  - _Requisitos: 8.1, 8.2, 8.5_

- [x] 17. Checkpoint final — Asegurarse de que todas las pruebas pasen, consultar al usuario si hay dudas.

## Notas

- Las tareas marcadas con `*` son opcionales y pueden omitirse para un MVP más rápido
- Cada tarea referencia los requisitos específicos para trazabilidad
- Las pruebas de propiedad usan **jqwik** con mínimo 100 iteraciones (`@Property(tries = 100)`)
- Las pruebas de integración usan **H2** en memoria como base de datos de prueba
- El formato de hora AM/PM del Excel (`10:56:56 a. m.`) requiere un `DateTimeFormatter` personalizado en el parser
