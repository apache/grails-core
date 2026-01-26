package org.grails.orm.hibernate.cfg.domainbinding;

import java.util.Optional;

import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.Mapping;

public class RootMappingFetcher {

    public Mapping getRootMapping(PersistentEntity referenced) {
        return Optional.ofNullable(referenced)
                .map(PersistentEntity::getRootEntity)
                .filter(GrailsHibernatePersistentEntity.class::isInstance)
                .map(GrailsHibernatePersistentEntity.class::cast)
                .map(GrailsHibernatePersistentEntity::getMappedForm)
                .orElse(null);
    }
}
