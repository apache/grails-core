package org.grails.scaffolding.model.property

import org.grails.datastore.mapping.model.PersistentProperty

/**
 * A factory to create instances of {@link DomainProperty}
 *
 * @author James Kleeh
 */
interface DomainPropertyFactory {

    /**
     * @param persistentProperty The persistent property
     * @return The {@link DomainProperty} representing the {@link PersistentProperty}
     */
    DomainProperty build(PersistentProperty persistentProperty)

    /**
     * @param rootProperty The root property. Typically an instance of {@link org.grails.datastore.mapping.model.types.Embedded}
     * @param persistentProperty The persistent property
     * @return The {@link DomainProperty} representing the {@link PersistentProperty}
     */
    DomainProperty build(PersistentProperty rootProperty, PersistentProperty persistentProperty)

}
