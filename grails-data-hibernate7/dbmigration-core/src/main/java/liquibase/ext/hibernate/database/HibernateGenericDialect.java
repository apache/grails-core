package liquibase.ext.hibernate.database;

import liquibase.exception.DatabaseException;
import org.hibernate.dialect.Dialect;

import java.sql.Types;

/**
 * Generic hibernate dialect used when an actual dialect cannot be determined.
 */
public class HibernateGenericDialect extends Dialect {
    public HibernateGenericDialect() throws DatabaseException {
        super();
        registerColumnType(Types.BIGINT, "bigint");
        registerColumnType(Types.BOOLEAN, "boolean");
        registerColumnType(Types.BLOB, "blob");
        registerColumnType(Types.CLOB, "clob");
        registerColumnType(Types.DATE, "date");
        registerColumnType(Types.FLOAT, "float");
        registerColumnType(Types.TIME, "time");
        registerColumnType(Types.TIMESTAMP, "timestamp");
        registerColumnType(Types.VARCHAR, "varchar($l)");
        registerColumnType(Types.BINARY, "binary");
        registerColumnType(Types.BIT, "boolean");
        registerColumnType(Types.CHAR, "char($l)");
        registerColumnType(Types.DECIMAL, "decimal($p,$s)");
        registerColumnType(Types.NUMERIC, "decimal($p,$s)");
        registerColumnType(Types.DOUBLE, "double");
        registerColumnType(Types.INTEGER, "integer");
        registerColumnType(Types.LONGVARBINARY, "longvarbinary");
        registerColumnType(Types.LONGVARCHAR, "longvarchar");
        registerColumnType(Types.REAL, "real");
        registerColumnType(Types.SMALLINT, "smallint");
        registerColumnType(Types.TINYINT, "tinyint");
    }

}
