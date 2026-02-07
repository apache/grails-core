package org.grails.orm.hibernate.cfg.domainbinding.secondpass;

import java.util.Map;

import jakarta.annotation.Nonnull;

import org.hibernate.MappingException;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.mapping.Collection;

import org.grails.orm.hibernate.cfg.GrailsDomainBinder;
import org.grails.orm.hibernate.cfg.HibernateToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.CollectionBinder;
import org.grails.orm.hibernate.cfg.domainbinding.MapSecondPassBinder;

public class MapSecondPass extends GrailsCollectionSecondPass {
    private static final long serialVersionUID = -3244991685626409031L;

    private final MapSecondPassBinder mapSecondPassBinder;

    public MapSecondPass(GrailsDomainBinder grailsDomainBinder, CollectionBinder collectionBinder, MapSecondPassBinder mapSecondPassBinder, HibernateToManyProperty property, @Nonnull InFlightMetadataCollector mappings,
                         Collection coll, String sessionFactoryBeanName) {
        super(grailsDomainBinder, collectionBinder, property, mappings, coll, sessionFactoryBeanName);
        this.mapSecondPassBinder = mapSecondPassBinder;
    }



    @SuppressWarnings("rawtypes")
    @Override
    public void doSecondPass(Map persistentClasses) throws MappingException {
        collectionBinder.bindCollectionSecondPass(property, mappings, persistentClasses, collection, sessionFactoryBeanName);
        mapSecondPassBinder.bindMapSecondPass(property, mappings, persistentClasses,
                (org.hibernate.mapping.Map) collection, sessionFactoryBeanName);
        createCollectionKeys();
    }
}
