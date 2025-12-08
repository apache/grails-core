package org.grails.orm.hibernate

import groovy.transform.CompileStatic
import org.hibernate.boot.Metadata
import org.hibernate.boot.spi.BootstrapContext
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.integrator.spi.Integrator
import org.hibernate.service.spi.SessionFactoryServiceRegistry

@CompileStatic
class MetadataIntegrator implements Integrator {

    Metadata metadata

    @Override
    void integrate(Metadata metadata, BootstrapContext bootstrapContext,
                   SessionFactoryImplementor sessionFactory) {
        this.metadata = metadata
    }

    @Override
    void disintegrate(SessionFactoryImplementor sessionFactory, SessionFactoryServiceRegistry serviceRegistry) {
        // noop
    }
}
