package org.grails.orm.hibernate.cfg.domainbinding.collectionType;

import java.util.Collection;

import org.hibernate.mapping.PersistentClass;
import org.hibernate.boot.spi.MetadataBuildingContext;

public class BagCollectionType extends CollectionType {

    public BagCollectionType(MetadataBuildingContext buildingContext) {
        super(Collection.class, buildingContext);
    }

    @Override
    public org.hibernate.mapping.Collection createCollection(PersistentClass owner) {
        return new org.hibernate.mapping.Bag(buildingContext, owner);
    }

}