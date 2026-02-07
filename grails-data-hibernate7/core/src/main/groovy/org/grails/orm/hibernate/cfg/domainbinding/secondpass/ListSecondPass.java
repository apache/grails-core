package org.grails.orm.hibernate.cfg.domainbinding.secondpass;

import java.io.Serial;
import java.util.Map;

import jakarta.annotation.Nonnull;

import org.hibernate.MappingException;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.mapping.Collection;

import org.grails.orm.hibernate.cfg.GrailsDomainBinder;
import org.grails.orm.hibernate.cfg.HibernateToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.CollectionBinder;

public class ListSecondPass implements org.hibernate.boot.spi.SecondPass, GrailsSecondPass {
    @Serial
    private static final long serialVersionUID = -3024674993774205193L;

    protected final GrailsDomainBinder grailsDomainBinder;
    protected final CollectionBinder collectionBinder;
    private final ListSecondPassBinder listSecondPassBinder;
    protected final HibernateToManyProperty property;
    protected final @Nonnull InFlightMetadataCollector mappings;
    protected final Collection collection;
    protected final String sessionFactoryBeanName;

    public ListSecondPass(GrailsDomainBinder grailsDomainBinder, CollectionBinder collectionBinder, ListSecondPassBinder listSecondPassBinder, HibernateToManyProperty property, @Nonnull InFlightMetadataCollector mappings,
                          Collection coll, String sessionFactoryBeanName) {
        this.grailsDomainBinder = grailsDomainBinder;
        this.collectionBinder = collectionBinder;
        this.listSecondPassBinder = listSecondPassBinder;
        this.property = property;
        this.mappings = mappings;
        this.collection = coll;
        this.sessionFactoryBeanName = sessionFactoryBeanName;
    }


    @SuppressWarnings("rawtypes")
    @Override
    public void doSecondPass(Map persistentClasses) throws MappingException {
        listSecondPassBinder.bindListSecondPass(property, mappings, persistentClasses,
                (org.hibernate.mapping.List) collection, sessionFactoryBeanName);
        createCollectionKeys(collection);
    }


}

