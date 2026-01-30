package org.grails.orm.hibernate.cfg;

import java.util.List;
import java.util.Optional;

import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.orm.hibernate.cfg.domainbinding.ConfigureDerivedPropertiesConsumer;

/**
 * Common interface for Hibernate persistent entities
 */
public interface GrailsHibernatePersistentEntity extends PersistentEntity {
    Mapping getMappedForm();

    default String getDiscriminatorValue() {
       return Optional.ofNullable(getMappedForm())
                .map(Mapping::getDiscriminator)
                .map(DiscriminatorConfig::getValue)
                .orElse(getName());

    }

    boolean forGrailsDomainMapping(String dataSourceName);

    boolean usesConnectionSource(String dataSourceName);

    PersistentProperty[] getCompositeIdentity();

    boolean isAbstract();


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

}
