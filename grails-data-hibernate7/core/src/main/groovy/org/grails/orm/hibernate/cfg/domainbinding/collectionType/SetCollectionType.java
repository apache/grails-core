package org.grails.orm.hibernate.cfg.domainbinding.collectionType;

import java.util.Set;

import org.hibernate.mapping.Collection;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.boot.spi.MetadataBuildingContext;

public class SetCollectionType extends CollectionType {

    public SetCollectionType(MetadataBuildingContext buildingContext) {
        super(Set.class, buildingContext);
    }

    @Override
    public Collection createCollection(PersistentClass owner) {
        return new org.hibernate.mapping.Set(buildingContext, owner);
    }

}