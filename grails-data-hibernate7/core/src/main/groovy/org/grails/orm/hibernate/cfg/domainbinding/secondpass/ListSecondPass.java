package org.grails.orm.hibernate.cfg.domainbinding.secondpass;

import java.util.Map;

import jakarta.annotation.Nonnull;

import org.hibernate.MappingException;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.mapping.Collection;

import org.grails.orm.hibernate.cfg.GrailsDomainBinder;
import org.grails.orm.hibernate.cfg.HibernateToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.CollectionBinder;
import org.grails.orm.hibernate.cfg.domainbinding.ListSecondPassBinder;

public class ListSecondPass extends GrailsCollectionSecondPass {
    private static final long serialVersionUID = -3024674993774205193L;

    private final ListSecondPassBinder listSecondPassBinder;

    public ListSecondPass(GrailsDomainBinder grailsDomainBinder, CollectionBinder collectionBinder, ListSecondPassBinder listSecondPassBinder, HibernateToManyProperty property, @Nonnull InFlightMetadataCollector mappings,
                          Collection coll, String sessionFactoryBeanName) {
        super(grailsDomainBinder, collectionBinder, property, mappings, coll, sessionFactoryBeanName);
        this.listSecondPassBinder = listSecondPassBinder;
    }


    @SuppressWarnings("rawtypes")
    @Override
    public void doSecondPass(Map persistentClasses) throws MappingException {
        collectionBinder.bindCollectionSecondPass(property, mappings, persistentClasses, collection, sessionFactoryBeanName);
        listSecondPassBinder.bindListSecondPass(property, mappings, persistentClasses,
                (org.hibernate.mapping.List) collection, sessionFactoryBeanName);
        createCollectionKeys();
    }
}
