package org.grails.orm.hibernate.cfg.domainbinding;

import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.reflect.NameUtils;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;

import java.util.Optional;

import static org.grails.orm.hibernate.cfg.GrailsDomainBinder.FOREIGN_KEY_SUFFIX;
import static org.hibernate.boot.model.naming.Identifier.toIdentifier;

/**
 * A wrapper for the Hibernate 6 PhysicalNamingStrategy to adapt it
 * for use within the Grails binding process, using a functional style.
 */
public class NamingStrategyWrapper {

    private final PhysicalNamingStrategy namingStrategy;
    private final JdbcEnvironment jdbcEnvironment;

    public NamingStrategyWrapper(PhysicalNamingStrategy namingStrategy, JdbcEnvironment jdbcEnvironment) {
        if (namingStrategy == null) {
            throw new IllegalArgumentException("PhysicalNamingStrategy argument cannot be null");
        }
        if (jdbcEnvironment == null) {
            throw new IllegalArgumentException("JdbcEnvironment argument cannot be null");
        }
        this.namingStrategy = namingStrategy;
        this.jdbcEnvironment = jdbcEnvironment;
    }

    public String getColumnName(String logicalName) {
        return Optional.ofNullable(logicalName)
                .flatMap(name ->
                        // Safely handle a null return from the strategy by wrapping it in an Optional.
                        Optional.ofNullable(namingStrategy.toPhysicalColumnName(toIdentifier(name), jdbcEnvironment))
                )
                .map(Identifier::getText)
                // Per Hibernate contract, if the strategy returns null, use the original logical name.
                .orElse(logicalName);
    }

    public String getTableName(String logicalName) {
        return Optional.ofNullable(logicalName)
                .flatMap(name ->
                        // Safely handle a null return from the strategy.
                        Optional.ofNullable(namingStrategy.toPhysicalTableName(toIdentifier(name), jdbcEnvironment))
                )
                .map(Identifier::getText)
                // Per Hibernate contract, if the strategy returns null, use the original logical name.
                .orElse(logicalName);
    }

    public String getForeignKeyForPropertyDomainClass(PersistentProperty property) {
        return Optional.ofNullable(property)
                .map(PersistentProperty::getOwner)
                .map(PersistentEntity::getJavaClass)
                .map(Class::getSimpleName)
                .map(NameUtils::decapitalize)
                .map(this::getColumnName)
                .filter(name -> !name.isBlank())
                .map(columnName -> columnName + FOREIGN_KEY_SUFFIX)
                .orElse(null);
    }

}