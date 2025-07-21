package org.grails.orm.hibernate.cfg.domainbinding;

import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;

import static org.hibernate.boot.model.naming.Identifier.toIdentifier;

public class NamingStrategyWrapper {

    private final PhysicalNamingStrategy namingStrategy;
    private final JdbcEnvironment jdbcEnvironment;

    public NamingStrategyWrapper(PhysicalNamingStrategy namingStrategy, JdbcEnvironment jdbcEnvironment) {
        this.namingStrategy = namingStrategy;
        this.jdbcEnvironment = jdbcEnvironment;
    }

    public String getColumnName(String oldColumnName) {
       return  namingStrategy.toPhysicalColumnName(toIdentifier(oldColumnName), jdbcEnvironment).toString();
    }

    public String getTableName(String oldTableName) {
        return namingStrategy.toPhysicalTableName(toIdentifier(oldTableName),jdbcEnvironment).toString();
    }
}
