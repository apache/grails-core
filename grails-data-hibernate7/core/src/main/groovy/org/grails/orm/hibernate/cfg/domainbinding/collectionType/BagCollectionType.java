package org.grails.orm.hibernate.cfg.domainbinding.collectionType;

import java.util.Collection;

import org.hibernate.mapping.PersistentClass;

import org.grails.orm.hibernate.cfg.GrailsDomainBinder;

public class BagCollectionType extends CollectionType {

    public BagCollectionType(GrailsDomainBinder binder) {
        super(Collection.class, binder);
    }

    @Override
    public org.hibernate.mapping.Collection createCollection(PersistentClass owner) {
        return new org.hibernate.mapping.Bag(buildingContext, owner);
    }

}