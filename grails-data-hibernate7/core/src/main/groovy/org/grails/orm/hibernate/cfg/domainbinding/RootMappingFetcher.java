package org.grails.orm.hibernate.cfg.domainbinding;

import java.util.Optional;

import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.orm.hibernate.cfg.HibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.Mapping;

public class RootMappingFetcher {

    public Mapping getRootMapping(PersistentEntity referenced) {
        return Optional.ofNullable(referenced)
                .map(PersistentEntity::getRootEntity)
                .filter(HibernatePersistentEntity.class::isInstance)
                .map(HibernatePersistentEntity.class::cast)
                .map(HibernatePersistentEntity::getMappedForm)
                .orElse(null);
    }
}
