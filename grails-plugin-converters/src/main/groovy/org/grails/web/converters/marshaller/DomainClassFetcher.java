package org.grails.web.converters.marshaller;

import grails.core.GrailsApplication;
import org.grails.datastore.mapping.model.PersistentEntity;

public interface DomainClassFetcher {
    PersistentEntity findDomainClass(Object instance);
}
