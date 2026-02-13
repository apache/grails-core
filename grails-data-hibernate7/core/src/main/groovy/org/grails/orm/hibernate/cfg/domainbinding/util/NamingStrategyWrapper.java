package org.grails.orm.hibernate.cfg.domainbinding.util;

import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.reflect.NameUtils;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;

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
public class NamingStrategyWrapper implements PersistentEntityNamingStrategy {

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

    @Override
    public String resolveColumnName(String logicalName) {
        return Optional.ofNullable(logicalName)
                .flatMap(name ->
                        // Safely handle a null return from the strategy by wrapping it in an Optional.
                        Optional.ofNullable(namingStrategy.toPhysicalColumnName(toIdentifier(name.replace('.', '_')), jdbcEnvironment))
                )
                .map(Identifier::getText)
                // Per Hibernate contract, if the strategy returns null, use the original logical name.
                .orElse(logicalName);
    }

    @Override
    public String resolveTableName(String logicalName) {
        return Optional.ofNullable(logicalName)
                .flatMap(name ->
                        // Safely handle a null return from the strategy.
                        Optional.ofNullable(namingStrategy.toPhysicalTableName(toIdentifier(name.replace('.', '_')), jdbcEnvironment))
                )
                .map(Identifier::getText)
                // Per Hibernate contract, if the strategy returns null, use the original logical name.
                .orElse(logicalName);
    }

    @Override
    public String resolveForeignKeyForPropertyDomainClass(PersistentProperty property) {
        return Optional.ofNullable(property)
                .map(PersistentProperty::getOwner)
                .filter(GrailsHibernatePersistentEntity.class::isInstance)
                .map(GrailsHibernatePersistentEntity.class::cast)
                .map(GrailsHibernatePersistentEntity::getJavaClass)
                .map(Class::getSimpleName)
                .map(NameUtils::decapitalize)
                .map(this::resolveColumnName)
                .filter(name -> !name.isBlank())
                .map(columnName -> columnName + FOREIGN_KEY_SUFFIX)
                .orElse(null);
    }

    @Override
    public String resolveTableName(GrailsHibernatePersistentEntity entity) {
        return resolveTableName(entity.getJavaClass().getSimpleName());
    }

}
