# Documento de Requisitos: Sistema de Punto de Venta (POS)

## Introducción

Sistema de punto de venta (POS) de escritorio desarrollado en Java con JavaFX, instalable de forma nativa en Windows. El sistema permite gestionar el inventario de productos y registrar ventas con su respectiva contabilidad, orientado a pequeños y medianos comercios.

## Glosario

- **Sistema_POS**: La aplicación de punto de venta en su totalidad.
- **Producto**: Artículo registrado en el inventario con nombre, precio, categoría y cantidad disponible.
- **Inventario**: Conjunto de productos registrados con sus cantidades disponibles.
- **Venta**: Transacción comercial que registra uno o más productos vendidos a un cliente.
- **Línea_de_Venta**: Ítem individual dentro de una venta, compuesto por un producto y una cantidad.
- **Ticket**: Documento generado al completar una venta con el detalle de productos, cantidades y totales.
- **Cajero**: Usuario operador del sistema que realiza ventas.
- **Administrador**: Usuario con permisos completos para gestionar inventario, usuarios y reportes.
- **Reporte**: Resumen estadístico de ventas o inventario en un período determinado.
- **Stock**: Cantidad disponible de un producto en el inventario.

---

## Requisitos

### Requisito 1: Gestión de Productos en el Inventario

**Historia de usuario:** Como administrador, quiero registrar y gestionar productos en el inventario, para mantener actualizado el catálogo de artículos disponibles para la venta.

#### Criterios de Aceptación

1. THE Sistema_POS SHALL permitir crear un producto con nombre, descripción, precio de venta, precio de costo, categoría, código de barras y cantidad inicial en stock.
2. WHEN un administrador registra un producto con un código de barras ya existente, THE Sistema_POS SHALL rechazar el registro y mostrar un mensaje de error indicando que el código ya está en uso.
3. WHEN un administrador actualiza los datos de un producto, THE Sistema_POS SHALL guardar los cambios y reflejarlos inmediatamente en el inventario.
4. WHEN un administrador elimina un producto, THE Sistema_POS SHALL marcarlo como inactivo sin eliminar el historial de ventas asociado.
5. THE Sistema_POS SHALL permitir buscar productos por nombre, categoría o código de barras.
6. IF el stock de un producto llega a cero o cae por debajo del nivel mínimo configurado, THEN THE Sistema_POS SHALL mostrar una alerta visual al administrador.

### Requisito 2: Control de Stock

**Historia de usuario:** Como administrador, quiero controlar las entradas y salidas de stock, para mantener el inventario actualizado con precisión.

#### Criterios de Aceptación

1. WHEN se completa una venta, THE Sistema_POS SHALL reducir automáticamente el stock de cada producto vendido según la cantidad registrada en la venta.
2. WHEN un administrador registra una entrada de mercancía, THE Sistema_POS SHALL incrementar el stock del producto correspondiente.
3. THE Sistema_POS SHALL registrar cada movimiento de stock con fecha, hora, tipo de movimiento (entrada/salida/ajuste) y usuario responsable.
4. IF un cajero intenta vender una cantidad mayor al stock disponible, THEN THE Sistema_POS SHALL impedir la venta y mostrar el stock actual disponible.
5. THE Sistema_POS SHALL permitir realizar ajustes manuales de inventario con justificación obligatoria.

### Requisito 3: Procesamiento de Ventas

**Historia de usuario:** Como cajero, quiero registrar ventas de forma rápida y precisa, para atender a los clientes eficientemente.

#### Criterios de Aceptación

1. WHEN un cajero agrega un producto a la venta por código de barras o búsqueda, THE Sistema_POS SHALL mostrar el nombre, precio unitario y subtotal actualizado.
2. WHEN un cajero modifica la cantidad de una línea de venta, THE Sistema_POS SHALL recalcular el subtotal y el total de la venta inmediatamente.
3. WHEN un cajero elimina una línea de venta, THE Sistema_POS SHALL actualizar el total de la venta.
4. THE Sistema_POS SHALL calcular el total de la venta sumando todos los subtotales de las líneas de venta.
5. WHEN un cajero completa una venta, THE Sistema_POS SHALL registrar la venta con fecha, hora, usuario, lista de productos, cantidades, precios y total.
6. WHEN una venta es completada exitosamente, THE Sistema_POS SHALL generar un ticket con el detalle completo de la transacción.
7. IF un cajero intenta completar una venta sin líneas de venta, THEN THE Sistema_POS SHALL impedir la operación y mostrar un mensaje de advertencia.
8. WHEN un cajero cancela una venta en curso, THE Sistema_POS SHALL descartar todos los cambios sin afectar el inventario ni el registro de ventas.

### Requisito 4: Métodos de Pago

**Historia de usuario:** Como cajero, quiero registrar el método de pago utilizado en cada venta, para llevar un control preciso de los ingresos por tipo de pago.

#### Criterios de Aceptación

1. THE Sistema_POS SHALL aceptar los métodos de pago: efectivo, tarjeta de crédito y tarjeta de débito.
2. WHEN el método de pago es efectivo, THE Sistema_POS SHALL calcular y mostrar el cambio a devolver al cliente.
3. WHEN el monto recibido en efectivo es menor al total de la venta, THE Sistema_POS SHALL impedir completar la venta y mostrar la diferencia pendiente.
4. THE Sistema_POS SHALL registrar el método de pago utilizado en cada venta.

### Requisito 5: Reportes de Ventas

**Historia de usuario:** Como administrador, quiero consultar reportes de ventas, para analizar el desempeño comercial del negocio.

#### Criterios de Aceptación

1. THE Sistema_POS SHALL generar un reporte de ventas por rango de fechas que incluya: número de ventas, total recaudado, desglose por método de pago y productos más vendidos.
2. THE Sistema_POS SHALL generar un reporte de ventas diario con el resumen del día actual.
3. WHEN un administrador consulta un reporte, THE Sistema_POS SHALL mostrar los datos actualizados al momento de la consulta.
4. THE Sistema_POS SHALL permitir exportar los reportes en formato PDF.
5. THE Sistema_POS SHALL mostrar en el reporte el total de ventas agrupado por categoría de producto.

### Requisito 6: Reporte de Inventario

**Historia de usuario:** Como administrador, quiero consultar el estado actual del inventario, para tomar decisiones de reabastecimiento.

#### Criterios de Aceptación

1. THE Sistema_POS SHALL generar un reporte de inventario que muestre todos los productos activos con su stock actual, precio de costo y precio de venta.
2. THE Sistema_POS SHALL mostrar en el reporte de inventario el valor total del inventario (suma de stock × precio de costo por producto).
3. THE Sistema_POS SHALL listar los productos con stock por debajo del nivel mínimo configurado en una sección destacada del reporte.
4. THE Sistema_POS SHALL permitir exportar el reporte de inventario en formato PDF.

### Requisito 7: Gestión de Usuarios y Acceso

**Historia de usuario:** Como administrador, quiero gestionar los usuarios del sistema, para controlar quién puede acceder y qué operaciones puede realizar.

#### Criterios de Aceptación

1. THE Sistema_POS SHALL requerir autenticación con nombre de usuario y contraseña para acceder al sistema.
2. THE Sistema_POS SHALL soportar dos roles: Administrador y Cajero, con permisos diferenciados.
3. WHEN un usuario con rol Cajero intenta acceder a funciones de administración, THE Sistema_POS SHALL denegar el acceso y mostrar un mensaje informativo.
4. THE Sistema_POS SHALL permitir al administrador crear, editar y desactivar cuentas de usuario.
5. IF un usuario ingresa credenciales incorrectas 5 veces consecutivas, THEN THE Sistema_POS SHALL bloquear la cuenta temporalmente por 15 minutos.
6. WHEN un administrador restablece la contraseña de un usuario, THE Sistema_POS SHALL requerir que el usuario cambie la contraseña en el siguiente inicio de sesión.

### Requisito 8: Instalación y Operación en Windows

**Historia de usuario:** Como administrador de TI, quiero instalar el sistema como aplicación nativa en Windows, para que funcione sin dependencias externas complejas.

#### Criterios de Aceptación

1. THE Sistema_POS SHALL distribuirse como un instalador ejecutable (.exe) para Windows 10 y Windows 11.
2. THE Sistema_POS SHALL soportar SQLite como base de datos embebida por defecto, sin requerir instalación de servidor externo.
3. WHERE el administrador configure una base de datos externa, THE Sistema_POS SHALL soportar conexión a PostgreSQL o MySQL como alternativa a SQLite.
4. THE Sistema_POS SHALL almacenar todos los datos localmente en el equipo donde está instalado cuando se usa SQLite.
4. WHEN la aplicación se inicia por primera vez, THE Sistema_POS SHALL ejecutar la configuración inicial creando la base de datos y el usuario administrador por defecto.
5. THE Sistema_POS SHALL funcionar sin conexión a internet.

### Requisito 9: Importación de Datos desde Excel

**Historia de usuario:** Como administrador, quiero importar mis datos históricos desde un archivo Excel, para migrar mis registros existentes al sistema sin tener que ingresarlos manualmente.

#### Criterios de Aceptación

1. THE Sistema_POS SHALL aceptar archivos Excel (.xlsx o .xls) con múltiples hojas, donde cada hoja está nombrada con el mes y día de las ventas que contiene, y cada hoja tiene exactamente 3 columnas: artículo (nombre del producto), precio (de venta, numérico) y hora de venta (formato 12 horas con AM/PM, ej: `10:56:56 a. m.`). La fecha completa de cada venta se construye combinando el nombre de la hoja (fecha) con la hora de la columna 3.
2. WHEN el administrador importa un archivo Excel, THE Sistema_POS SHALL crear el producto en el inventario solo si no existe previamente (deduplicación por nombre de artículo), con stock inicial de 100 unidades y precio de costo en 0 para que el administrador lo complete posteriormente.
3. WHEN el administrador importa un archivo Excel, THE Sistema_POS SHALL registrar cada fila como una venta individual con la fechahora completa construida a partir del nombre de la hoja y la hora de la columna 3, sin importar si el mismo artículo aparece múltiples veces.
4. IF una fila del Excel contiene datos inválidos (precio no numérico, hora con formato incorrecto, artículo vacío), THEN THE Sistema_POS SHALL omitir esa fila, registrar el error y continuar con las demás filas.
5. WHEN la importación finaliza, THE Sistema_POS SHALL mostrar un resumen con el número de productos creados, ventas registradas y filas con errores.
6. THE Sistema_POS SHALL permitir previsualizar los datos del Excel antes de confirmar la importación.
7. IF el archivo Excel no tiene el formato esperado (columnas incorrectas o faltantes, o nombre de hoja que no puede interpretarse como fecha), THEN THE Sistema_POS SHALL mostrar un mensaje de error descriptivo sin realizar ninguna importación.
