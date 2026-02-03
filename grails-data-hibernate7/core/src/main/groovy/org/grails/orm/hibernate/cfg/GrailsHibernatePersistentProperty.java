package org.grails.orm.hibernate.cfg;

import org.grails.datastore.mapping.model.PersistentProperty;

import java.util.Optional;

/**
 * Interface for Hibernate persistent properties
 */
public interface GrailsHibernatePersistentProperty extends PersistentProperty<PropertyConfig> {

    /**
     * @param config The property config
     * @param mapping The mapping
     * @return The type name
     */
    default String getTypeName(PropertyConfig config, Mapping mapping) {
        return Optional.ofNullable(config.getType())
                .map(typeObj -> typeObj instanceof Class<?> clazz ? clazz.getName() : typeObj.toString())
                .orElseGet(() -> mapping != null ? mapping.getTypeName(getType()) : null);
    }

    /**
     * @param mapping The mapping
     * @return The type name
     */
    default String getTypeName(Mapping mapping) {
        return getTypeName(getMappedForm(), mapping);
    }

    /**
     * @return The type name
     */
    default String getTypeName() {
        return getTypeName(getHibernateOwner().getMappedForm());
    }

    default HibernatePersistentEntity getHibernateOwner() {
        return (HibernatePersistentEntity) getOwner();
    }

    default Class<?> getUserType() {
        Object typeObj = getMappedForm().getType();
        Class<?> userType = null;
        if (typeObj instanceof Class<?>) {
            userType = (Class<?>)typeObj;
        } else if (typeObj != null) {
            String typeName = typeObj.toString();
            try {
                userType = Class.forName(typeName, true, Thread.currentThread().getContextClassLoader());
            } catch (ClassNotFoundException e) {

            }
        }
        return userType;
    };
}
