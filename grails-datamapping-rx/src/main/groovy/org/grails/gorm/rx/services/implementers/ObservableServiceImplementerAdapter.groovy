package org.grails.gorm.rx.services.implementers

import groovy.transform.CompileStatic
import org.grails.datastore.gorm.services.ServiceImplementer
import org.grails.datastore.gorm.services.ServiceImplementerAdapter
import org.grails.datastore.gorm.services.implementers.AbstractProjectionImplementer
import org.grails.datastore.gorm.services.implementers.IterableServiceImplementer
import org.grails.datastore.gorm.services.implementers.SingleResultServiceImplementer

/**
 * Adapts {@link ServiceImplementer} implementations for use in RxJava
 *
 * @since 6.1.1
 * @author Graeme Rocher
 */
@CompileStatic
class ObservableServiceImplementerAdapter implements ServiceImplementerAdapter {
    @Override
    ServiceImplementer adapt(ServiceImplementer implementer) {
        if(implementer instanceof SingleResultServiceImplementer) {
            return new SingleResultAdapter((SingleResultServiceImplementer)implementer)
        }
        else if(implementer instanceof IterableServiceImplementer) {
            return new ObservableResultAdapter((IterableServiceImplementer)implementer)
        }
        return null
    }

}
