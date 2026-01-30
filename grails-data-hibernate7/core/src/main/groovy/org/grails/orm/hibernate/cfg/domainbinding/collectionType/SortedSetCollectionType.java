package org.grails.orm.hibernate.cfg.domainbinding.collectionType;

import java.util.Set;
import java.util.SortedSet;

import org.hibernate.MappingException;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.PersistentClass;

import jakarta.annotation.Nonnull;

import org.grails.datastore.mapping.model.types.ToMany;
import org.grails.orm.hibernate.cfg.GrailsDomainBinder;

public class SortedSetCollectionType extends CollectionType {

    public SortedSetCollectionType(GrailsDomainBinder binder) {
        super(SortedSet.class, binder);
    }

    @Override
    public Collection create(ToMany property, PersistentClass owner,
                             String path, @Nonnull InFlightMetadataCollector mappings, String sessionFactoryBeanName) throws MappingException {
        org.hibernate.mapping.Set coll = new org.hibernate.mapping.Set(buildingContext, owner);
        coll.setCollectionTable(owner.getTable());
        coll.setTypeName(getTypeName(property));
        binder.bindCollection(property, coll, owner, mappings, path, sessionFactoryBeanName);
        return coll;
    }

}
