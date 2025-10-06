package org.grails.orm.hibernate.cfg.domainbinding;

import java.util.Optional;

import org.hibernate.FetchMode;
import org.hibernate.mapping.ManyToOne;

import org.grails.datastore.mapping.model.types.Association;
import org.grails.orm.hibernate.cfg.PropertyConfig;

public class ManyToOneValuesBinder {
    private final PersistentPropertyToPropertyConfig persistentPropertyToPropertyConfig;

    public ManyToOneValuesBinder() {
        this.persistentPropertyToPropertyConfig = new PersistentPropertyToPropertyConfig();
    }

    protected ManyToOneValuesBinder(PersistentPropertyToPropertyConfig persistentPropertyToPropertyConfig) {
        this.persistentPropertyToPropertyConfig = persistentPropertyToPropertyConfig;
    }

    public void bindManyToOneValues(Association property, ManyToOne manyToOne) {
        PropertyConfig config = persistentPropertyToPropertyConfig.toPropertyConfig(property);

        var fetchMode = Optional.ofNullable(config.getFetchMode()).orElse(FetchMode.DEFAULT);
        manyToOne.setFetchMode(fetchMode);


        var lazy = Optional.ofNullable(config.getLazy()).orElse(property != null);
        manyToOne.setLazy(lazy);

        manyToOne.setIgnoreNotFound(config.getIgnoreNotFound());

        // set referenced entity
        manyToOne.setReferencedEntityName(property.getAssociatedEntity().getName());
    }
}
