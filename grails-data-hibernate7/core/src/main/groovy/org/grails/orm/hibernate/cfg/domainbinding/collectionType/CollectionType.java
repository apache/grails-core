package org.grails.orm.hibernate.cfg.domainbinding.collectionType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import org.hibernate.MappingException;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.Bag;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.PersistentClass;

import jakarta.annotation.Nonnull;

import org.grails.orm.hibernate.cfg.GrailsDomainBinder;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.HibernateToManyProperty;
import org.grails.orm.hibernate.cfg.Mapping;

/**
 * A Collection type, for the moment only Set is supported
 *
 * @author Graeme
 */
public abstract class CollectionType {

    protected final Class<?> clazz;
    protected final GrailsDomainBinder binder;
    protected final MetadataBuildingContext buildingContext;

    public abstract Collection createCollection(PersistentClass owner);


    public Collection create(HibernateToManyProperty property
            , PersistentClass owner
            ,  String path
            , @Nonnull InFlightMetadataCollector mappings
            , String sessionFactoryBeanName) throws MappingException {
        Collection coll = createCollection(owner);
        coll.setCollectionTable(owner.getTable());
        coll.setTypeName(getTypeName(property));
        return coll;
    }

    protected CollectionType(Class<?> clazz, GrailsDomainBinder binder) {
        this.clazz = clazz;
        this.binder = binder;
        this.buildingContext = binder.getMetadataBuildingContext();
    }

    @Override
    public String toString() {
        return clazz.getName();
    }



    public String getTypeName(HibernateToManyProperty property) {
        return property.getTypeName();
    }

}
