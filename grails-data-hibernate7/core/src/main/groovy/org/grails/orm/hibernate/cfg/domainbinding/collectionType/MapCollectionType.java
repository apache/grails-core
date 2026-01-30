package org.grails.orm.hibernate.cfg.domainbinding.collectionType;

import java.util.Map;

import org.hibernate.MappingException;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.PersistentClass;

import jakarta.annotation.Nonnull;

import org.grails.datastore.mapping.model.types.ToMany;
import org.grails.orm.hibernate.cfg.GrailsDomainBinder;

public class MapCollectionType extends CollectionType {

    public MapCollectionType(GrailsDomainBinder binder) {
        super(Map.class, binder);
    }

    @Override
    public Collection create(ToMany property, PersistentClass owner,
                             String path, @Nonnull InFlightMetadataCollector mappings, String sessionFactoryBeanName) throws MappingException {
        org.hibernate.mapping.Map map = new org.hibernate.mapping.Map(buildingContext, owner);
        map.setTypeName(getTypeName(property));
        binder.bindCollection(property, map, owner, mappings, path, sessionFactoryBeanName);
        return map;
    }

}
