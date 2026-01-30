package org.grails.orm.hibernate.cfg.domainbinding.collectionType;

import org.hibernate.MappingException;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.mapping.Bag;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.PersistentClass;

import jakarta.annotation.Nonnull;

import org.grails.datastore.mapping.model.types.ToMany;
import org.grails.orm.hibernate.cfg.GrailsDomainBinder;

public class BagCollectionType extends CollectionType {

    public BagCollectionType(GrailsDomainBinder binder) {
        super(java.util.Collection.class, binder);
    }

    @Override
    public Collection create(ToMany property, PersistentClass owner,
                             String path, @Nonnull InFlightMetadataCollector mappings, String sessionFactoryBeanName) throws MappingException {
        Bag coll = new Bag(buildingContext, owner);
        coll.setCollectionTable(owner.getTable());
        coll.setTypeName(getTypeName(property));
        binder.bindCollection(property, coll, owner, mappings, path, sessionFactoryBeanName);
        return coll;
    }

}
