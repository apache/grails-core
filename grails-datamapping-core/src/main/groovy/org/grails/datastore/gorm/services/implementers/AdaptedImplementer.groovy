package org.grails.datastore.gorm.services.implementers

import org.grails.datastore.gorm.services.ServiceImplementer

/**
 * An interface for adapters to return the original implementer
 *
 * @author Graeme Rocher
 * @since 6.1.1
 */
interface AdaptedImplementer {
    ServiceImplementer getAdapted()
}