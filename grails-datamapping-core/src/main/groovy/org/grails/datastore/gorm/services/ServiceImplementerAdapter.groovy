package org.grails.datastore.gorm.services

/**
 * Allows adapting an implementer from one type to another
 *
 * @author Graeme Rocher
 * @since 6.1.1
 */
interface ServiceImplementerAdapter {
    /**
     * Adapt the implementer, returning a new implementer if possible, otherwise null
     *
     * @param implementer The implementer
     * @return The adapted implementer or null
     */
    ServiceImplementer adapt(ServiceImplementer implementer)
}