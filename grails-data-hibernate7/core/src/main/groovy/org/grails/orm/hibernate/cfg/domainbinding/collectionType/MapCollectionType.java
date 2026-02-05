package org.grails.orm.hibernate.cfg.domainbinding.collectionType;

import java.util.Map;

import org.hibernate.mapping.Collection;
import org.hibernate.mapping.PersistentClass;

import org.grails.orm.hibernate.cfg.GrailsDomainBinder;

public class MapCollectionType extends CollectionType {

    public MapCollectionType(GrailsDomainBinder binder) {
        super(Map.class, binder);
    }

    @Override
    public Collection createCollection(PersistentClass owner) {
        return new org.hibernate.mapping.Map(buildingContext, owner);
    }

}