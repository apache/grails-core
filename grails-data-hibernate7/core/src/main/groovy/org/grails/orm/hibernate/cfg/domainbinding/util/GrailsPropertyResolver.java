package org.grails.orm.hibernate.cfg.domainbinding.util;

import org.hibernate.MappingException;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;

/**
 * Utility class for resolving Grails properties from PersistentClass.
 */
public class GrailsPropertyResolver {

    /**
     * Retrieves a property from a PersistentClass, with a fallback for composite primary keys.
     *
     * @param associatedClass The PersistentClass to get the property from.
     * @param propertyName    The name of the property to retrieve.
     * @return The resolved Property.
     * @throws MappingException if the property cannot be found.
     */
    public Property getProperty(PersistentClass associatedClass, String propertyName) throws MappingException {
        try {
            return associatedClass.getProperty(propertyName);
        }
        catch (MappingException e) {
            // maybe it's squirreled away in a composite primary key
            if (associatedClass.getKey() instanceof Component) {
                return ((Component) associatedClass.getKey()).getProperty(propertyName);
            }
            throw e;
        }
    }
}
