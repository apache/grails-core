package org.grails.orm.hibernate.cfg;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import jakarta.annotation.Nonnull;

import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.relational.Database;
import org.hibernate.boot.model.relational.Namespace;
import org.hibernate.boot.spi.InFlightMetadataCollector;

import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.datastore.mapping.model.types.TenantId;
import org.grails.orm.hibernate.cfg.domainbinding.ConfigureDerivedPropertiesConsumer;
import org.grails.orm.hibernate.cfg.domainbinding.DefaultColumnNameFetcher;
import org.grails.orm.hibernate.cfg.domainbinding.NamespaceNameExtractor;

/**
 * Common interface for Hibernate persistent entities
 */
public interface GrailsHibernatePersistentEntity extends PersistentEntity {
    Mapping getMappedForm();

    @Override
    GrailsHibernatePersistentProperty getIdentity();

    @Override
    GrailsHibernatePersistentProperty[] getCompositeIdentity();

    default String getDiscriminatorValue() {
        return Optional.ofNullable(getMappedForm())
                .map(Mapping::getDiscriminator)
                .map(DiscriminatorConfig::getValue)
                .orElse(getName());

    }



    boolean forGrailsDomainMapping(String dataSourceName);

    boolean usesConnectionSource(String dataSourceName);

    boolean isAbstract();

    /**
     * @return The properties that should be bound to the Hibernate meta model
     */
    default List<GrailsHibernatePersistentProperty> getPersistentPropertiesToBind() {
        List<PersistentProperty> properties = getPersistentProperties();
        if (properties == null) {
            return java.util.Collections.emptyList();
        }
        return properties.stream()
                .filter(Objects::nonNull)
                .filter(p -> p.getMappedForm() != null)
                .filter(p -> !p.isIdentityProperty())
                .filter(p -> !p.isCompositeIdProperty())
                .filter(p -> !GormProperties.VERSION.equals(p.getName()))
                .filter(p -> !p.isInherited())
                .map(p -> (GrailsHibernatePersistentProperty) p)
                .toList();
    }

    @Override
    GrailsHibernatePersistentProperty getVersion();

    default List<GrailsHibernatePersistentEntity> getChildEntities(String dataSourceName) {
        return getMappingContext()
                .getDirectChildEntities(this)
                .stream()
                .filter(GrailsHibernatePersistentEntity.class::isInstance)
                .map(GrailsHibernatePersistentEntity.class::cast)
                .filter(persistentEntity -> persistentEntity.usesConnectionSource(dataSourceName))
                .filter(sub -> sub.getJavaClass().getSuperclass().equals(this.getJavaClass()))
                .toList();
    }

    default boolean isComponentPropertyNullable(PersistentProperty embeddedProperty) {
        if (embeddedProperty == null) return false;
        final Mapping mapping = getMappedForm();
        return !isRoot() && (mapping == null || mapping.isTablePerHierarchy()) || embeddedProperty.isNullable();
    }

    default void configureDerivedProperties(){
        getPersistentProperties().forEach(new ConfigureDerivedPropertiesConsumer( getMappedForm()));
    }

    default String getMultiTenantFilterCondition(DefaultColumnNameFetcher fetcher) {
        return Optional.ofNullable(getTenantId())
                .map(fetcher::getDefaultColumnName)
                .map(defaultColumnName -> ":tenantId = " + defaultColumnName)
                .orElse(null);
    }
    default String getSchema(@Nonnull InFlightMetadataCollector mappings) {
        return Optional.ofNullable(getMappedForm())
                .map(Mapping::getTable)
                .map(org.grails.orm.hibernate.cfg.Table::getSchema)
                .orElse(NamespaceNameExtractor.getSchemaName(mappings));

    }

    default String getCatalog(@Nonnull InFlightMetadataCollector mappings) {
        return Optional.ofNullable(getMappedForm())
                .map(Mapping::getTable)
                .map(org.grails.orm.hibernate.cfg.Table::getCatalog)
                .orElse(NamespaceNameExtractor.getCatalogName(mappings));

    }
}

