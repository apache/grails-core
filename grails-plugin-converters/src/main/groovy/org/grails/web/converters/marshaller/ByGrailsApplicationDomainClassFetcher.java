package org.grails.web.converters.marshaller;

import grails.core.GrailsApplication;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.web.converters.marshaller.DomainClassFetcher;

public class ByGrailsApplicationDomainClassFetcher implements DomainClassFetcher {
    GrailsApplication grailsApplication;

    public ByGrailsApplicationDomainClassFetcher(GrailsApplication grailsApplication) {
        this.grailsApplication = grailsApplication;
    }

    @Override
    public PersistentEntity findDomainClass(Object instance) {
        return grailsApplication.getMappingContext().getPersistentEntity(instance.getClass().getName());
    }
}
