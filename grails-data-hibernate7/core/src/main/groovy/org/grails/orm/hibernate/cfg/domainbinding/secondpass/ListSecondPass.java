package org.grails.orm.hibernate.cfg.domainbinding.secondpass;

import java.util.Map;

import jakarta.annotation.Nonnull;

import org.hibernate.MappingException;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.mapping.Collection;

import org.grails.orm.hibernate.cfg.GrailsDomainBinder;
import org.grails.orm.hibernate.cfg.HibernateToManyProperty;

public class ListSecondPass extends GrailsCollectionSecondPass {
    private static final long serialVersionUID = -3024674993774205193L;


    public ListSecondPass(GrailsDomainBinder grailsDomainBinder, HibernateToManyProperty property, @Nonnull InFlightMetadataCollector mappings,
                          Collection coll, String sessionFactoryBeanName) {
        super(grailsDomainBinder, property, mappings, coll, sessionFactoryBeanName);
    }

    @Override
    public void doSecondPass(Map<?, ?> persistentClasses, Map<?, ?> inheritedMetas) throws MappingException {
        grailsDomainBinder.bindListSecondPass(property, mappings, persistentClasses,
                (org.hibernate.mapping.List) collection, sessionFactoryBeanName);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void doSecondPass(Map persistentClasses) throws MappingException {
        grailsDomainBinder.bindListSecondPass(property, mappings, persistentClasses,
                (org.hibernate.mapping.List) collection, sessionFactoryBeanName);
    }
}
