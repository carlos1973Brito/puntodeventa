package com.pos.config;

import com.pos.model.Categoria;
import com.pos.model.LineaVenta;
import com.pos.model.MovimientoStock;
import com.pos.model.Producto;
import com.pos.model.Usuario;
import com.pos.model.Venta;
import com.pos.model.enums.DatabaseType;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;

/**
 * Gestiona la configuración de la conexión a la base de datos.
 * Lee config.properties para determinar el tipo de BD y configura Hibernate.
 * Soporta SQLite (default), PostgreSQL y MySQL.
 */
public class DatabaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);
    private static final String CONFIG_FILE = "/config.properties";

    private static SessionFactory sessionFactory;
    private static DatabaseType databaseType;

    private DatabaseConfig() {}

    /**
     * Construye y retorna el SessionFactory de Hibernate.
     * Usa el tipo de BD definido en config.properties.
     */
    public static synchronized SessionFactory buildSessionFactory() {
        if (sessionFactory != null && !sessionFactory.isClosed()) {
            return sessionFactory;
        }

        Properties appProps = loadAppProperties();
        databaseType = resolveDatabaseType(appProps);

        logger.info("Inicializando base de datos con tipo: {}", databaseType);

        Configuration hibernateConfig = new Configuration();
        hibernateConfig.configure("/hibernate.cfg.xml");

        applyDatabaseProperties(hibernateConfig, appProps, databaseType);

        // Registrar entidades
        hibernateConfig.addAnnotatedClass(Categoria.class);
        hibernateConfig.addAnnotatedClass(Usuario.class);
        hibernateConfig.addAnnotatedClass(Producto.class);
        hibernateConfig.addAnnotatedClass(MovimientoStock.class);
        hibernateConfig.addAnnotatedClass(Venta.class);
        hibernateConfig.addAnnotatedClass(LineaVenta.class);

        sessionFactory = hibernateConfig.buildSessionFactory();
        logger.info("SessionFactory creado exitosamente.");

        // Migrar schema si es SQLite (actualizar CHECK constraints de enums)
        if (databaseType == DatabaseType.SQLITE) {
            migrarSQLite(appProps);
        }

        return sessionFactory;
    }

    /**
     * Retorna el tipo de base de datos configurado.
     */
    public static DatabaseType getDatabaseType() {
        if (databaseType == null) {
            Properties props = loadAppProperties();
            databaseType = resolveDatabaseType(props);
        }
        return databaseType;
    }

    /**
     * Cierra el SessionFactory si está abierto.
     */
    public static synchronized void shutdown() {
        if (sessionFactory != null && !sessionFactory.isClosed()) {
            sessionFactory.close();
            logger.info("SessionFactory cerrado.");
        }
    }

    // -------------------------------------------------------------------------
    // Métodos privados
    // -------------------------------------------------------------------------

    private static Properties loadAppProperties() {
        Properties props = new Properties();
        try (InputStream is = DatabaseConfig.class.getResourceAsStream(CONFIG_FILE)) {
            if (is == null) {
                logger.warn("No se encontró {}. Usando valores por defecto (SQLite).", CONFIG_FILE);
                return props;
            }
            props.load(is);
        } catch (IOException e) {
            logger.error("Error al leer {}: {}", CONFIG_FILE, e.getMessage());
        }
        return props;
    }

    private static DatabaseType resolveDatabaseType(Properties props) {
        String type = props.getProperty("db.type", "SQLITE").toUpperCase().trim();
        try {
            return DatabaseType.valueOf(type);
        } catch (IllegalArgumentException e) {
            logger.warn("Tipo de BD desconocido '{}'. Usando SQLITE por defecto.", type);
            return DatabaseType.SQLITE;
        }
    }

    private static void applyDatabaseProperties(Configuration config,
                                                  Properties appProps,
                                                  DatabaseType type) {
        switch (type) {
            case SQLITE -> applySQLiteProperties(config, appProps);
            case POSTGRESQL -> applyPostgreSQLProperties(config, appProps);
            case MYSQL -> applyMySQLProperties(config, appProps);
        }
    }

    private static void applySQLiteProperties(Configuration config, Properties appProps) {
        String dbPath = appProps.getProperty("db.sqlite.path",
                System.getProperty("user.home") + "/PuntoDeVenta/pos.db");

        // Expandir variable ${user.home} si viene literal en el archivo
        dbPath = dbPath.replace("${user.home}", System.getProperty("user.home"));

        // Crear directorio si no existe
        java.io.File dbFile = new java.io.File(dbPath);
        if (dbFile.getParentFile() != null) {
            dbFile.getParentFile().mkdirs();
        }

        config.setProperty("hibernate.connection.driver_class", "org.sqlite.JDBC");
        config.setProperty("hibernate.connection.url", "jdbc:sqlite:" + dbPath);
        config.setProperty("hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect");
        config.setProperty("hibernate.hbm2ddl.auto", "update");
        config.setProperty("hibernate.show_sql", "false");
        config.setProperty("hibernate.format_sql", "false");
        // SQLite no soporta bien el pool de conexiones; usar una sola conexión
        config.setProperty("hibernate.connection.pool_size", "1");

        logger.info("SQLite configurado en: {}", dbPath);
    }

    private static void applyPostgreSQLProperties(Configuration config, Properties appProps) {
        String host = appProps.getProperty("db.postgresql.host", "localhost");
        String port = appProps.getProperty("db.postgresql.port", "5432");
        String dbName = appProps.getProperty("db.postgresql.database", "pos");
        String user = appProps.getProperty("db.postgresql.username", "postgres");
        String password = appProps.getProperty("db.postgresql.password", "");

        config.setProperty("hibernate.connection.driver_class", "org.postgresql.Driver");
        config.setProperty("hibernate.connection.url",
                "jdbc:postgresql://" + host + ":" + port + "/" + dbName);
        config.setProperty("hibernate.connection.username", user);
        config.setProperty("hibernate.connection.password", password);
        config.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        config.setProperty("hibernate.hbm2ddl.auto", "update");
        config.setProperty("hibernate.show_sql", "false");

        logger.info("PostgreSQL configurado en: {}:{}/{}", host, port, dbName);
    }

    private static void applyMySQLProperties(Configuration config, Properties appProps) {
        String host = appProps.getProperty("db.mysql.host", "localhost");
        String port = appProps.getProperty("db.mysql.port", "3306");
        String dbName = appProps.getProperty("db.mysql.database", "pos");
        String user = appProps.getProperty("db.mysql.username", "root");
        String password = appProps.getProperty("db.mysql.password", "");

        config.setProperty("hibernate.connection.driver_class", "com.mysql.cj.jdbc.Driver");
        config.setProperty("hibernate.connection.url",
                "jdbc:mysql://" + host + ":" + port + "/" + dbName
                + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true");
        config.setProperty("hibernate.connection.username", user);
        config.setProperty("hibernate.connection.password", password);
        config.setProperty("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
        config.setProperty("hibernate.hbm2ddl.auto", "update");
        config.setProperty("hibernate.show_sql", "false");

        logger.info("MySQL configurado en: {}:{}/{}", host, port, dbName);
    }

    /**
     * Migración para SQLite: recrea la tabla 'venta' si el CHECK constraint
     * de 'estado' no incluye los nuevos valores CREDITO y DEVUELTA.
     */
    private static void migrarSQLite(Properties appProps) {
        String dbPath = appProps.getProperty("db.sqlite.path",
                System.getProperty("user.home") + "/PuntoDeVenta/pos.db");
        dbPath = dbPath.replace("${user.home}", System.getProperty("user.home"));

        String url = "jdbc:sqlite:" + dbPath;
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {

            // Verificar si quedó una migración incompleta (venta_old existe pero venta no)
            ResultSet rsOld = stmt.executeQuery(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='venta_old'");
            boolean ventaOldExists = rsOld.next() && rsOld.getInt(1) > 0;

            ResultSet rsNew = stmt.executeQuery(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='venta'");
            boolean ventaExists = rsNew.next() && rsNew.getInt(1) > 0;

            // Recuperar migración incompleta: venta_old existe pero venta no
            if (ventaOldExists && !ventaExists) {
                logger.warn("Detectada migración incompleta: venta_old existe pero venta no. Recuperando...");
                conn.setAutoCommit(false);
                try {
                    stmt.execute("ALTER TABLE venta_old RENAME TO venta");
                    conn.commit();
                    logger.info("Recuperación completada: venta_old renombrada a venta.");
                } catch (Exception e) {
                    conn.rollback();
                    logger.error("Error al recuperar migración incompleta: {}", e.getMessage(), e);
                    return;
                }
                // Recargar estado
                ventaExists = true;
                ventaOldExists = false;
            }

            // Limpiar venta_old si quedó huérfana
            if (ventaOldExists && ventaExists) {
                logger.warn("Limpiando tabla venta_old huérfana...");
                stmt.execute("DROP TABLE IF EXISTS venta_old");
            }

            if (!ventaExists) {
                logger.warn("La tabla 'venta' no existe, Hibernate la creará.");
                return;
            }

            // Verificar si el constraint actual ya incluye CREDITO y DEVUELTA
            ResultSet rs = stmt.executeQuery(
                "SELECT sql FROM sqlite_master WHERE type='table' AND name='venta'");
            if (!rs.next()) return;

            String ddl = rs.getString("sql");
            if (ddl == null || (ddl.contains("'CREDITO'") && ddl.contains("'DEVUELTA'"))) {
                logger.debug("Tabla 'venta' ya tiene los CHECK constraints actualizados.");
                return; // Ya está actualizado
            }

            logger.info("Migrando tabla 'venta': actualizando CHECK constraint de estado...");
            conn.setAutoCommit(false);
            try {
                stmt.execute("ALTER TABLE venta RENAME TO venta_old");
                stmt.execute(
                    "CREATE TABLE venta (" +
                    "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "  cajero_id INTEGER REFERENCES usuario(id)," +
                    "  fecha TIMESTAMP NOT NULL," +
                    "  total NUMERIC(10,2) NOT NULL DEFAULT 0," +
                    "  metodo_pago VARCHAR(20)," +
                    "  monto_recibido NUMERIC(10,2)," +
                    "  cambio NUMERIC(10,2)," +
                    "  estado VARCHAR(20) NOT NULL DEFAULT 'EN_CURSO'" +
                    "    CHECK(estado IN ('EN_CURSO','COMPLETADA','CANCELADA','CREDITO','DEVUELTA'))," +
                    "  saldo_pendiente NUMERIC(10,2) DEFAULT 0," +
                    "  nombre_cliente VARCHAR(200)," +
                    "  motivo_devolucion VARCHAR(500)," +
                    "  fecha_devolucion TIMESTAMP" +
                    ")"
                );
                stmt.execute(
                    "INSERT INTO venta (id, cajero_id, fecha, total, metodo_pago," +
                    "  monto_recibido, cambio, estado, saldo_pendiente," +
                    "  nombre_cliente, motivo_devolucion, fecha_devolucion)" +
                    " SELECT id, cajero_id, fecha, total, metodo_pago," +
                    "  monto_recibido, cambio, estado," +
                    "  COALESCE(saldo_pendiente, 0)," +
                    "  nombre_cliente, motivo_devolucion, fecha_devolucion" +
                    " FROM venta_old"
                );
                stmt.execute("DROP TABLE venta_old");
                conn.commit();
                logger.info("Migración de tabla 'venta' completada exitosamente.");
            } catch (Exception e) {
                conn.rollback();
                logger.error("Error durante migración de tabla 'venta': {}", e.getMessage(), e);
            }
        } catch (Exception e) {
            logger.error("Error al conectar para migración SQLite: {}", e.getMessage(), e);
        }
    }
}
