package org.grails.orm.hibernate.cfg.domainbinding.collectionType;

import java.util.List;

import org.hibernate.mapping.Collection;
import org.hibernate.mapping.PersistentClass;

import org.grails.orm.hibernate.cfg.GrailsDomainBinder;

public class ListCollectionType extends CollectionType {

    public ListCollectionType(GrailsDomainBinder binder) {
        super(List.class, binder);
    }

    @Override
    public Collection createCollection(PersistentClass owner) {
        return new org.hibernate.mapping.List(buildingContext, owner);
    }

}