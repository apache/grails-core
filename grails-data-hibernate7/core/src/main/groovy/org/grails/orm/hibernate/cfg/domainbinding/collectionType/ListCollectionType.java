package org.grails.orm.hibernate.cfg.domainbinding.collectionType;

import java.util.List;

import org.hibernate.mapping.Collection;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.boot.spi.MetadataBuildingContext;

public class ListCollectionType extends CollectionType {

    public ListCollectionType(MetadataBuildingContext buildingContext) {
        super(List.class, buildingContext);
    }

    @Override
    public Collection createCollection(PersistentClass owner) {
        return new org.hibernate.mapping.List(buildingContext, owner);
    }

}