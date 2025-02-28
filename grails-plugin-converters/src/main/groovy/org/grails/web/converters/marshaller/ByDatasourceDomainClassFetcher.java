package org.grails.web.converters.marshaller;

import org.grails.datastore.gorm.GormEnhancer;
import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.web.converters.marshaller.DomainClassFetcher;

public class ByDatasourceDomainClassFetcher implements DomainClassFetcher {

    @Override
    public PersistentEntity findDomainClass(Object instance) {
        Class clazz = instance.getClass();
        Datastore datastore = GormEnhancer.findDatastore(clazz);
        if ( datastore != null) {
            MappingContext mappingContext = datastore.getMappingContext();
            if ( mappingContext != null ) {
                return mappingContext.getPersistentEntity(clazz.getName());
            }
        }
        return null;
    }
}
