package org.grails.orm.hibernate

import groovy.transform.CompileStatic
import org.hibernate.boot.Metadata
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.integrator.spi.Integrator
import org.hibernate.service.spi.SessionFactoryServiceRegistry

@CompileStatic
class MetadataIntegrator implements Integrator {

    Metadata metadata

    @Override
    void integrate(Metadata metadata, SessionFactoryImplementor sessionFactory, SessionFactoryServiceRegistry serviceRegistry) {
        this.metadata = metadata
    }

    @Override
    void disintegrate(SessionFactoryImplementor sessionFactory, SessionFactoryServiceRegistry serviceRegistry) {
        // noop
    }
}
