package org.grails.orm.hibernate.cfg;

import org.grails.datastore.mapping.model.PersistentProperty;

import java.util.Optional;

/**
 * Interface for Hibernate persistent properties
 */
public interface GrailsHibernatePersistentProperty extends PersistentProperty<PropertyConfig> {

    /**
     * @param mapping The mapping
     * @return The type name
     */
    default String getTypeName(Mapping mapping) {
        return Optional.ofNullable(getMappedForm().getType())
                .map(typeObj -> typeObj instanceof Class<?> clazz ? clazz.getName() : typeObj.toString())
                .orElseGet(() -> mapping != null ? mapping.getTypeName(getType()) : null);
    }
}
