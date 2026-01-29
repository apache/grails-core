package org.grails.orm.hibernate.cfg.domainbinding.collectionType;

import java.util.List;

import org.hibernate.MappingException;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.PersistentClass;

import org.grails.datastore.mapping.model.types.ToMany;
import org.grails.orm.hibernate.cfg.GrailsDomainBinder;

public class ListCollectionType extends CollectionType {

    public ListCollectionType(GrailsDomainBinder binder) {
        super(List.class, binder);
    }

    @Override
    public Collection create(ToMany property, PersistentClass owner,
                             String path, InFlightMetadataCollector mappings, String sessionFactoryBeanName) throws MappingException {
        org.hibernate.mapping.List coll = new org.hibernate.mapping.List(buildingContext, owner);
        coll.setCollectionTable(owner.getTable());
        coll.setTypeName(getTypeName(property));
        binder.bindCollection(property, coll, owner, mappings, path, sessionFactoryBeanName);
        return coll;
    }

}
