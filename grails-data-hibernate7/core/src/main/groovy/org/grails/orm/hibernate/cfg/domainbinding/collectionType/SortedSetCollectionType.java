package org.grails.orm.hibernate.cfg.domainbinding.collectionType;

import java.util.SortedSet;

import org.hibernate.mapping.Collection;
import org.hibernate.mapping.PersistentClass;

import org.grails.orm.hibernate.cfg.GrailsDomainBinder;

public class SortedSetCollectionType extends CollectionType {

    public SortedSetCollectionType(GrailsDomainBinder binder) {
        super(SortedSet.class, binder);
    }

    @Override
    public Collection createCollection(PersistentClass owner) {
        return new org.hibernate.mapping.Set(buildingContext, owner);
    }

}