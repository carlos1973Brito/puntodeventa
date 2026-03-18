# Sistema Punto de Venta (POS)

Aplicación de escritorio para Windows desarrollada con **JavaFX 21** e **Hibernate 6** sobre **SQLite**.
Cubre el ciclo completo de una tienda: ventas, inventario, créditos, devoluciones, reportes e impresión de tickets térmicos ESC/POS.

---

## Tabla de contenidos

1. [Requisitos](#requisitos)
2. [Estructura del proyecto](#estructura-del-proyecto)
3. [Arquitectura](#arquitectura)
4. [Módulos y clases](#módulos-y-clases)
   - [Modelos (entidades)](#modelos-entidades)
   - [Repositorios](#repositorios)
   - [Servicios](#servicios)
   - [Controladores JavaFX](#controladores-javafx)
   - [Configuración](#configuración)
5. [Base de datos](#base-de-datos)
6. [Configuración del sistema](#configuración-del-sistema)
7. [Impresora térmica](#impresora-térmica)
8. [Roles y seguridad](#roles-y-seguridad)
9. [Cómo ejecutar](#cómo-ejecutar)
10. [Cómo empaquetar](#cómo-empaquetar)
11. [Dependencias principales](#dependencias-principales)

---

## Requisitos

| Herramienta | Versión mínima |
|-------------|---------------|
| JDK         | 17            |
| Maven       | 3.8+          |
| Windows     | 10 / 11       |
| WiX Toolset | 3.x (solo para generar instalador `.exe`) |

---

## Estructura del proyecto

```
punto-de-venta/
├── pom.xml                          # Dependencias y plugins Maven
└── src/
    ├── main/
    │   ├── java/com/pos/
    │   │   ├── MainApp.java          # Punto de entrada JavaFX
    │   │   ├── config/
    │   │   │   └── DatabaseConfig.java
    │   │   ├── model/                # Entidades JPA
    │   │   │   ├── Categoria.java
    │   │   │   ├── LineaVenta.java
    │   │   │   ├── MovimientoStock.java
    │   │   │   ├── Producto.java
    │   │   │   ├── Usuario.java
    │   │   │   ├── Venta.java
    │   │   │   └── enums/
    │   │   │       ├── EstadoVenta.java
    │   │   │       ├── MetodoPago.java
    │   │   │       ├── Rol.java
    │   │   │       └── TipoMovimiento.java
    │   │   ├── repository/           # Interfaces + implementaciones Hibernate
    │   │   │   ├── ProductoRepository.java
    │   │   │   ├── VentaRepository.java
    │   │   │   ├── MovimientoStockRepository.java
    │   │   │   ├── UsuarioRepository.java
    │   │   │   └── impl/
    │   │   ├── service/              # Lógica de negocio
    │   │   │   ├── VentaService.java
    │   │   │   ├── ProductoService.java
    │   │   │   ├── UsuarioService.java
    │   │   │   ├── StockService.java
    │   │   │   ├── ReporteService.java
    │   │   │   ├── TicketPrinterService.java
    │   │   │   ├── ConfiguracionService.java
    │   │   │   └── impl/
    │   │   ├── controller/           # Controladores JavaFX (MVC)
    │   │   │   ├── MainController.java
    │   │   │   ├── LoginController.java
    │   │   │   ├── VentaController.java
    │   │   │   ├── HistorialController.java
    │   │   │   ├── InventarioController.java
    │   │   │   ├── ProductoController.java
    │   │   │   ├── ReporteController.java
    │   │   │   ├── UsuarioController.java
    │   │   │   ├── ImportarController.java
    │   │   │   └── ConfiguracionController.java
    │   │   ├── dto/                  # Objetos de transferencia de datos
    │   │   └── exception/            # Excepciones del dominio
    │   └── resources/
    │       ├── fxml/                 # Vistas JavaFX
    │       ├── config.properties     # Configuración por defecto
    │       ├── hibernate.cfg.xml
    │       └── logback.xml
    └── test/                         # Tests JUnit 5 + jqwik (PBT)
```

---

## Arquitectura

El proyecto sigue el patrón **MVC** adaptado a JavaFX:

```
Vista (FXML)  ←→  Controlador (JavaFX)  ←→  Servicio  ←→  Repositorio  ←→  BD (SQLite)
```

- **Vista**: archivos `.fxml` en `src/main/resources/fxml/`
- **Controlador**: clases en `controller/` — solo lógica de UI, sin acceso directo a BD
- **Servicio**: clases en `service/impl/` — toda la lógica de negocio, validaciones y transacciones
- **Repositorio**: clases en `repository/impl/` — acceso a datos vía Hibernate Sessions
- **Modelo**: entidades JPA en `model/`

Las dependencias entre capas fluyen hacia abajo (Controller → Service → Repository → Model).
Los controladores nunca acceden directamente a los repositorios.

---

## Módulos y clases

### Modelos (entidades)

#### `Venta`
Representa una transacción de venta. Tabla: `venta`.

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | Long | PK autoincremental |
| `cajero` | Usuario | FK al usuario que realizó la venta |
| `fecha` | LocalDateTime | Fecha y hora de la venta |
| `total` | BigDecimal | Total calculado de todas las líneas |
| `metodoPago` | MetodoPago | EFECTIVO / TARJETA_CREDITO / TARJETA_DEBITO / CREDITO |
| `montoRecibido` | BigDecimal | Dinero entregado por el cliente |
| `cambio` | BigDecimal | Cambio devuelto (solo efectivo) |
| `estado` | EstadoVenta | EN_CURSO / COMPLETADA / CANCELADA / CREDITO / DEVUELTA |
| `saldoPendiente` | BigDecimal | Saldo restante en ventas a crédito |
| `nombreCliente` | String | Nombre del cliente (ventas a crédito) |
| `motivoDevolucion` | String | Motivo cuando `estado = DEVUELTA` |
| `lineas` | List\<LineaVenta\> | Productos incluidos en la venta |

#### `LineaVenta`
Un renglón dentro de una venta. Tabla: `linea_venta`.

| Campo | Descripción |
|-------|-------------|
| `producto` | FK al producto vendido |
| `nombreProducto` | Nombre capturado al momento de la venta (histórico) |
| `precioUnitario` | Precio al momento de la venta |
| `precioCosto` | Costo al momento de la venta (para margen) |
| `cantidad` | Unidades vendidas |
| `subtotal` | `precioUnitario × cantidad` |

#### `Producto`
Catálogo de productos. Tabla: `producto`.

| Campo | Descripción |
|-------|-------------|
| `nombre` | Nombre del producto |
| `codigoBarras` | Código único (puede ser null) |
| `precioVenta` | Precio de venta al público |
| `precioCosto` | Precio de costo |
| `stockActual` | Unidades disponibles |
| `stockMinimo` | Umbral de alerta de stock bajo |
| `activo` | Soft-delete: false = producto desactivado |
| `categoria` | FK a Categoria |

#### `Usuario`
Usuarios del sistema. Tabla: `usuario`.

| Campo | Descripción |
|-------|-------------|
| `username` | Nombre de usuario único |
| `passwordHash` | Hash BCrypt de la contraseña |
| `nombreCompleto` | Nombre para mostrar |
| `rol` | ADMINISTRADOR o CAJERO |
| `activo` | Soft-delete |
| `intentosFallidos` | Contador de intentos de login fallidos |
| `bloqueadoHasta` | Timestamp de desbloqueo automático |
| `debeCambiarPassword` | Flag para forzar cambio en próximo login |

#### `MovimientoStock`
Auditoría de todos los cambios de inventario. Tabla: `movimiento_stock`.

| Campo | Descripción |
|-------|-------------|
| `tipo` | ENTRADA / SALIDA / AJUSTE |
| `cantidad` | Unidades del movimiento |
| `stockAnterior` | Stock antes del movimiento |
| `stockNuevo` | Stock después del movimiento |
| `justificacion` | Motivo del movimiento |
| `usuario` | Quién realizó el movimiento |

#### `Categoria`
Categorías de productos. Tabla: `categoria`. Solo tiene `id` y `nombre`.

#### Enums

| Enum | Valores |
|------|---------|
| `EstadoVenta` | `EN_CURSO`, `COMPLETADA`, `CANCELADA`, `CREDITO`, `DEVUELTA` |
| `MetodoPago` | `EFECTIVO`, `TARJETA_CREDITO`, `TARJETA_DEBITO`, `CREDITO` |
| `Rol` | `ADMINISTRADOR`, `CAJERO` |
| `TipoMovimiento` | `ENTRADA`, `SALIDA`, `AJUSTE` |

---

### Repositorios

Cada repositorio tiene una interfaz en `repository/` y su implementación Hibernate en `repository/impl/`.
Todos reciben el `SessionFactory` por constructor (inyección manual).

#### `VentaRepository`
```java
Venta save(Venta v)
Optional<Venta> findById(Long id)
List<Venta> findByFechaBetween(LocalDateTime desde, LocalDateTime hasta)
Map<MetodoPago, BigDecimal> sumByMetodoPago(LocalDate desde, LocalDate hasta)
List<Venta> findCompletadasYCredito(LocalDateTime desde, LocalDateTime hasta)
List<Venta> findCreditos()                          // ventas con estado CREDITO
List<Venta> findByEstado(EstadoVenta estado)
```

#### `ProductoRepository`
```java
Producto save(Producto p)
Optional<Producto> findById(Long id)
Optional<Producto> findByCodigoBarras(String codigo)
List<Producto> findByNombreContainingOrCategoria(String termino)  // búsqueda para autocompletado
List<Producto> findAllActivos()
List<Producto> findBajoStockMinimo()
```

#### `MovimientoStockRepository`
```java
MovimientoStock save(MovimientoStock m)
List<MovimientoStock> findByProductoId(Long productoId)
List<MovimientoStock> findByFechaBetween(LocalDateTime desde, LocalDateTime hasta)
List<MovimientoStock> findAll()
```

#### `UsuarioRepository`
```java
Usuario save(Usuario u)
Optional<Usuario> findById(Long id)
Optional<Usuario> findByUsername(String username)
List<Usuario> findAll()
```

---

### Servicios

#### `VentaService` / `VentaServiceImpl`
Núcleo del POS. Gestiona el ciclo de vida completo de una venta.

| Método | Descripción |
|--------|-------------|
| `iniciarVenta(cajeroId)` | Crea una venta nueva en estado `EN_CURSO` |
| `agregarLinea(ventaId, codigoBarras, cantidad)` | Busca producto por código o nombre y lo agrega |
| `agregarLineaPorId(ventaId, productoId, cantidad)` | Agrega producto por ID (desde autocompletado) |
| `modificarCantidadLinea(ventaId, lineaId, nuevaCantidad)` | Actualiza cantidad de una línea |
| `eliminarLinea(ventaId, lineaId)` | Elimina una línea de la venta |
| `completarVenta(ventaId, metodo, montoRecibido)` | Cierra la venta, descuenta stock, registra movimientos |
| `cancelarVenta(ventaId)` | Cancela la venta sin afectar stock |
| `completarVentaCredito(ventaId, anticipo, nombreCliente)` | Cierra como crédito/apartado |
| `abonarCredito(ventaId, monto)` | Registra un abono a una venta a crédito |
| `listarCreditos()` | Lista todas las ventas con saldo pendiente |
| `devolverVenta(ventaId, motivo)` | Revierte la venta y repone el stock |

**Flujo de una venta normal:**
```
iniciarVenta() → agregarLinea() × N → completarVenta()
                                           ↓
                              descuenta stock de cada producto
                              registra MovimientoStock (SALIDA)
                              estado → COMPLETADA
```

#### `ProductoService` / `ProductoServiceImpl`
CRUD de productos con validaciones.

| Método | Descripción |
|--------|-------------|
| `crear(dto)` | Valida código de barras único, crea el producto |
| `actualizar(id, dto)` | Actualiza datos del producto |
| `desactivar(id)` | Soft-delete (activo = false) |
| `buscarPorCodigo(codigoBarras)` | Búsqueda exacta por código |
| `buscarPorNombreOCategoria(termino)` | Búsqueda parcial para autocompletado |
| `listarActivos()` | Todos los productos activos |
| `listarBajoStockMinimo()` | Productos con stock ≤ stockMinimo |

#### `UsuarioService` / `UsuarioServiceImpl`
Autenticación con BCrypt y bloqueo por intentos fallidos.

| Método | Descripción |
|--------|-------------|
| `crear(dto)` | Hashea la contraseña con BCrypt y guarda el usuario |
| `autenticar(username, password)` | Verifica credenciales; lanza `CredencialesInvalidasException` o `UsuarioBloqueadoException` |
| `registrarIntentoFallido(username)` | Incrementa contador; bloquea 15 min al llegar a 5 intentos |
| `estaBloqueado(username)` | Verifica si el bloqueo sigue vigente; lo limpia si expiró |
| `cambiarContrasena(id, nuevaContrasena)` | Actualiza hash y resetea intentos |
| `desactivar(id)` | Soft-delete del usuario |

**Política de bloqueo:** 5 intentos fallidos → bloqueo de 15 minutos. Se desbloquea automáticamente al expirar.

#### `StockService` / `StockServiceImpl`
Movimientos manuales de inventario.

| Método | Descripción |
|--------|-------------|
| `registrarEntrada(productoId, cantidad, justificacion, usuarioId)` | Suma stock, registra movimiento ENTRADA |
| `registrarAjuste(productoId, nuevaCantidad, justificacion, usuarioId)` | Establece stock absoluto, registra AJUSTE |
| `historialPorProducto(productoId)` | Lista todos los movimientos de un producto |

#### `TicketPrinterService` / `TicketPrinterServiceImpl`
Impresión ESC/POS vía `javax.print` (raw bytes). Compatible con impresoras térmicas de 58mm y 80mm instaladas en Windows.

| Método | Descripción |
|--------|-------------|
| `imprimirTicket(venta)` | Construye y envía el ticket completo a la impresora configurada |
| `imprimirPrueba(nombreImpresora)` | Imprime una página de prueba |
| `listarImpresoras()` | Retorna los nombres de todas las impresoras instaladas en el sistema |

**Comandos ESC/POS usados:**
- `ESC @` — Inicializar impresora
- `ESC a 0/1/2` — Alineación izquierda / centro / derecha
- `ESC ! 0x00/0x08/0x30` — Fuente normal / negrita / doble alto
- `GS V 66 0` — Corte parcial de papel

#### `ConfiguracionService`
Lee y escribe `config.properties` en dos capas:
1. Defaults del classpath (`src/main/resources/config.properties`)
2. Archivo editable del usuario: `%USERPROFILE%\PuntoDeVenta\config.properties` (tiene prioridad)

```java
config.get(PRINTER_NAME, "")        // nombre de la impresora
config.get(PRINTER_WIDTH, "32")     // 32 = 58mm, 48 = 80mm
config.get(NEGOCIO_NOMBRE, "")      // nombre del negocio para el ticket
config.get(NEGOCIO_DIRECCION, "")
config.get(NEGOCIO_TELEFONO, "")
config.get(NEGOCIO_MENSAJE, "")     // mensaje de pie de ticket
config.guardar()                    // persiste en el archivo del usuario
```

#### `ReporteService` / `ReporteServiceImpl`
Genera reportes de ventas e inventario exportables a PDF y Excel.

---

### Controladores JavaFX

Todos los controladores que necesitan saber el usuario autenticado implementan `MainController.UsuarioAwareController`:
```java
public interface UsuarioAwareController {
    void setUsuarioActual(Usuario usuario);
}
```
`MainController` llama a `setUsuarioActual()` automáticamente al cargar cada vista.

#### `MainController`
Pantalla principal con sidebar de navegación. Gestiona:
- Carga dinámica de vistas en el `StackPane` central
- Control de acceso: el botón "Usuarios" solo es visible para `ADMINISTRADOR`
- Estilo del botón activo en el sidebar
- Cierre de sesión (vuelve al login)

#### `LoginController`
- Valida que usuario y contraseña no estén vacíos
- Llama a `UsuarioService.autenticar()`
- Muestra mensajes de error específicos para credenciales inválidas y cuenta bloqueada
- Al autenticar exitosamente, carga `main.fxml` y pasa el usuario a `MainController`

#### `VentaController`
Pantalla principal del POS. Funcionalidades:
- **Autocompletado**: mientras el cajero escribe en `txtBusqueda`, consulta `ProductoService.buscarPorNombreOCategoria()` y muestra un `ContextMenu` con hasta 10 resultados
- **Agregar producto**: por código de barras, nombre o selección del autocompletado
- **Tabla de líneas**: muestra productos, precios, cantidades y subtotales; botón ✕ por fila para eliminar
- **Diálogo de cobro**: descuento (monto fijo o porcentaje), método de pago, cálculo de cambio en tiempo real
- **Ventas a crédito**: campo de anticipo y nombre del cliente
- **Vista previa del ticket**: ventana modal con el ticket en texto monoespaciado y botón "🖨 Imprimir" (deshabilitado si no hay impresora configurada)

#### `HistorialController`
Tres pestañas:
1. **Ventas**: filtro por rango de fechas, tabla con estado y saldo, botones Reimprimir y Devolver
2. **Créditos**: lista de ventas con saldo pendiente, botón Abonar con diálogo de monto
3. **Corte de caja**: resumen del día por método de pago y cajero, gráfica de barras de los últimos 7 días

#### `InventarioController`
Dos pestañas:
1. **Stock actual**: tabla de todos los productos con filtro por nombre/categoría, alerta visual (fila amarilla) para stock bajo
2. **Movimientos**: historial filtrable por producto, botones para registrar Entrada y Ajuste manual

#### `ProductoController`
CRUD completo de productos:
- Tabla con búsqueda en tiempo real
- Formulario de alta/edición con validaciones
- Desactivación (soft-delete)
- Importación desde Excel

#### `ReporteController`
Reportes de ventas por período con exportación a PDF y Excel.

#### `UsuarioController`
Gestión de usuarios (solo ADMINISTRADOR):
- Alta de usuarios con rol
- Cambio de contraseña
- Activar/desactivar

#### `ConfiguracionController`
- ComboBox con impresoras detectadas por `javax.print`
- Selección de ancho de papel (58mm / 80mm)
- Campos de datos del negocio para el encabezado del ticket
- Botón "Prueba" que imprime una página de prueba
- Botón "Refrescar" para redetectar impresoras

---

## Base de datos

Por defecto usa **SQLite** en `%USERPROFILE%\PuntoDeVenta\pos.db`.

Hibernate gestiona el schema con `hbm2ddl.auto=update`. Al iniciar, `DatabaseConfig` ejecuta una migración manual para SQLite que actualiza el `CHECK constraint` de la columna `estado` en la tabla `venta` si es necesario (para soportar los valores `CREDITO` y `DEVUELTA` añadidos en versiones posteriores).

### Cambiar a PostgreSQL o MySQL

Editar `%USERPROFILE%\PuntoDeVenta\config.properties`:

```properties
# PostgreSQL
db.type=POSTGRESQL
db.postgresql.host=localhost
db.postgresql.port=5432
db.postgresql.database=pos
db.postgresql.username=postgres
db.postgresql.password=tu_password

# MySQL
db.type=MYSQL
db.mysql.host=localhost
db.mysql.port=3306
db.mysql.database=pos
db.mysql.username=root
db.mysql.password=tu_password
```

> Para PostgreSQL y MySQL se deben agregar los drivers JDBC correspondientes al `pom.xml`.

---

## Configuración del sistema

El archivo de configuración del usuario se crea automáticamente en:
```
%USERPROFILE%\PuntoDeVenta\config.properties
```

Claves disponibles:

```properties
# Impresora térmica (nombre exacto como aparece en Windows)
ticket.printer.name=EPSON TM-T20III
# Ancho en caracteres: 32 para 58mm, 48 para 80mm
ticket.printer.width=32

# Datos del negocio (aparecen en el encabezado del ticket)
ticket.negocio.nombre=Mi Tienda
ticket.negocio.direccion=Calle Principal 123
ticket.negocio.telefono=555-1234
ticket.negocio.mensaje=¡Gracias por su compra!

# Base de datos
db.type=SQLITE
db.sqlite.path=${user.home}/PuntoDeVenta/pos.db
```

---

## Impresora térmica

La impresión usa `javax.print` con bytes ESC/POS crudos — no requiere drivers especiales más allá del driver de Windows de la impresora.

**Pasos para configurar:**
1. Instalar la impresora en Windows normalmente
2. Abrir el sistema → Configuración → seleccionar la impresora del ComboBox
3. Elegir el ancho de papel (58mm = 32 chars, 80mm = 48 chars)
4. Clic en "Prueba" para verificar
5. Guardar

**Impresoras probadas:** EPSON TM series, Star Micronics, Bixolon, genéricas USB/serial.

---

## Roles y seguridad

| Función | CAJERO | ADMINISTRADOR |
|---------|--------|---------------|
| Realizar ventas | ✓ | ✓ |
| Ver historial | ✓ | ✓ |
| Gestionar inventario | ✓ | ✓ |
| Ver reportes | ✓ | ✓ |
| Gestionar productos | ✓ | ✓ |
| Gestionar usuarios | ✗ | ✓ |
| Configuración | ✓ | ✓ |

**Contraseñas:** almacenadas con BCrypt (factor de costo por defecto de jBCrypt).

**Usuario por defecto** creado al primer inicio:
- Usuario: `admin`
- Contraseña: `admin1234`

> Cambiar la contraseña del admin en el primer uso desde Gestión de Usuarios.

---

## Cómo ejecutar

### Desde IntelliJ IDEA
1. Abrir el proyecto (`punto-de-venta/`)
2. Marcar como proyecto Maven si no lo detecta automáticamente
3. Run → `MainApp`

> Si hay errores de compilación por clases obsoletas: **Build → Clean Project** y luego **Rebuild Project**.

### Desde línea de comandos
```cmd
mvn -f punto-de-venta\pom.xml javafx:run
```

---

## Cómo empaquetar

Genera un instalador `.exe` para Windows con JRE embebido (no requiere Java instalado en el equipo destino).

**Requisitos previos:**
- JDK 17+ en el PATH
- [WiX Toolset 3.x](https://wixtoolset.org/) instalado

```cmd
mvn -f punto-de-venta\pom.xml package jpackage:jpackage
```

El instalador se genera en `punto-de-venta\target\dist\PuntoDeVenta-1.0.0.exe`.

---

## Dependencias principales

| Librería | Versión | Uso |
|----------|---------|-----|
| JavaFX | 21 | UI de escritorio |
| Hibernate ORM | 6.4.4 | ORM / acceso a datos |
| SQLite JDBC | 3.45.3 | Driver SQLite |
| hibernate-community-dialects | 6.4.4 | Dialecto SQLite para Hibernate |
| Apache POI | 5.2.5 | Importación/exportación Excel |
| OpenPDF | 1.3.43 | Exportación PDF |
| jBCrypt | 0.4 | Hash de contraseñas |
| Logback | 1.4.14 | Logging |
| JUnit 5 | 5.10.2 | Tests unitarios |
| jqwik | 1.8.4 | Property-based testing |
| Mockito | 5.11.0 | Mocks en tests |
