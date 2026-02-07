package org.grails.orm.hibernate.cfg.domainbinding.secondpass;

import java.util.Map;

import jakarta.annotation.Nonnull;

import org.hibernate.MappingException;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.mapping.Collection;

import org.grails.orm.hibernate.cfg.GrailsDomainBinder;
import org.grails.orm.hibernate.cfg.HibernateToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.CollectionBinder;

public class ListSecondPass extends GrailsCollectionSecondPass {
    private static final long serialVersionUID = -3024674993774205193L;


    public ListSecondPass(GrailsDomainBinder grailsDomainBinder, CollectionBinder collectionBinder, HibernateToManyProperty property, @Nonnull InFlightMetadataCollector mappings,
                          Collection coll, String sessionFactoryBeanName) {
        super(grailsDomainBinder, collectionBinder, property, mappings, coll, sessionFactoryBeanName);
    }


    @SuppressWarnings("rawtypes")
    @Override
    public void doSecondPass(Map persistentClasses) throws MappingException {
        collectionBinder.bindListSecondPass(property, mappings, persistentClasses,
                (org.hibernate.mapping.List) collection, sessionFactoryBeanName);
    }
}
