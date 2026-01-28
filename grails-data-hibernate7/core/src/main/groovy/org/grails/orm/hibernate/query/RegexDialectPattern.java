package org.grails.orm.hibernate.query;

import org.hibernate.dialect.*;
import java.util.Arrays;

public enum RegexDialectPattern {
    MYSQL(MySQLDialect.class, "?1 RLIKE ?2"),
    MARIADB(MariaDBDialect.class, "?1 REGEXP ?2"),
    POSTGRES(PostgreSQLDialect.class, "?1 ~ ?2"),
    ORACLE(OracleDialect.class, "REGEXP_LIKE(?1, ?2)"),
    H2(H2Dialect.class, "REGEXP_LIKE(?1, ?2)"),
    // Default fallback
    DEFAULT(Dialect.class, "?1 LIKE ?2");

    private final Class<? extends Dialect> dialectClass;
    private final String sqlPattern;

    RegexDialectPattern(Class<? extends Dialect> dialectClass, String sqlPattern) {
        this.dialectClass = dialectClass;
        this.sqlPattern = sqlPattern;
    }

    public String getSqlPattern() {
        return sqlPattern;
    }

    /**
     * Resolves the pattern by checking if the runtime dialect
     * is an instance of the supported dialect class.
     */
    public static String findPatternForDialect(Dialect runtimeDialect) {
        return Arrays.stream(values())
                .filter(p -> p != DEFAULT && p.dialectClass.isInstance(runtimeDialect))
                .findFirst()
                .map(RegexDialectPattern::getSqlPattern)
                .orElse(DEFAULT.sqlPattern);
    }
}
