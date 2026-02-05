package org.grails.orm.hibernate.cfg.domainbinding.collectionType;

import java.util.Set;

import org.hibernate.mapping.Collection;
import org.hibernate.mapping.PersistentClass;

import org.grails.orm.hibernate.cfg.GrailsDomainBinder;

public class SetCollectionType extends CollectionType {

    public SetCollectionType(GrailsDomainBinder binder) {
        super(Set.class, binder);
    }

    @Override
    public Collection createCollection(PersistentClass owner) {
        return new org.hibernate.mapping.Set(buildingContext, owner);
    }

}