package org.grails.orm.hibernate.cfg.domainbinding.secondpass;

import java.io.Serial;
import java.util.Map;

import jakarta.annotation.Nonnull;

import org.hibernate.MappingException;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.mapping.Collection;

import org.grails.orm.hibernate.cfg.GrailsDomainBinder;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.binder.CollectionBinder;

public class MapSecondPass implements org.hibernate.boot.spi.SecondPass, GrailsSecondPass {
    @Serial
    private static final long serialVersionUID = -3244991685626409031L;

    protected final GrailsDomainBinder grailsDomainBinder;
    protected final CollectionBinder collectionBinder;
    private final MapSecondPassBinder mapSecondPassBinder;
    protected final HibernateToManyProperty property;
    protected final @Nonnull InFlightMetadataCollector mappings;
    protected final Collection collection;
    protected final String sessionFactoryBeanName;

    public MapSecondPass(GrailsDomainBinder grailsDomainBinder, CollectionBinder collectionBinder, MapSecondPassBinder mapSecondPassBinder, HibernateToManyProperty property, @Nonnull InFlightMetadataCollector mappings,
                         Collection coll, String sessionFactoryBeanName) {
        this.grailsDomainBinder = grailsDomainBinder;
        this.collectionBinder = collectionBinder;
        this.mapSecondPassBinder = mapSecondPassBinder;
        this.property = property;
        this.mappings = mappings;
        this.collection = coll;
        this.sessionFactoryBeanName = sessionFactoryBeanName;
    }



    @SuppressWarnings("rawtypes")
    @Override
    public void doSecondPass(Map persistentClasses) throws MappingException {
        mapSecondPassBinder.bindMapSecondPass(property, mappings, persistentClasses,
                (org.hibernate.mapping.Map) collection, sessionFactoryBeanName);
        createCollectionKeys(collection);
    }


}
