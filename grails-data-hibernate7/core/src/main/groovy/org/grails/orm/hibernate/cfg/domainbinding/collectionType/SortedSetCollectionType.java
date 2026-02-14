package org.grails.orm.hibernate.cfg.domainbinding.collectionType;

import java.util.SortedSet;

import org.hibernate.mapping.Collection;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.boot.spi.MetadataBuildingContext;

public class SortedSetCollectionType extends CollectionType {

    public SortedSetCollectionType(MetadataBuildingContext buildingContext) {
        super(SortedSet.class, buildingContext);
    }

    @Override
    public Collection createCollection(PersistentClass owner) {
        return new org.hibernate.mapping.Set(buildingContext, owner);
    }

}