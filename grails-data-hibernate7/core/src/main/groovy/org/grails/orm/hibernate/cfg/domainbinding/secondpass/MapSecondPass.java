package org.grails.orm.hibernate.cfg.domainbinding.secondpass;

import java.util.Map;

import jakarta.annotation.Nonnull;

import org.hibernate.MappingException;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.mapping.Collection;

import org.grails.datastore.mapping.model.types.ToMany;
import org.grails.orm.hibernate.cfg.GrailsDomainBinder;

public class MapSecondPass extends GrailsCollectionSecondPass {
    private static final long serialVersionUID = -3244991685626409031L;


    public MapSecondPass(GrailsDomainBinder grailsDomainBinder, ToMany property, @Nonnull InFlightMetadataCollector mappings,
                         Collection coll, String sessionFactoryBeanName) {
        super(grailsDomainBinder, property, mappings, coll, sessionFactoryBeanName);
    }

    @Override
    public void doSecondPass(Map<?, ?> persistentClasses, Map<?, ?> inheritedMetas) throws MappingException {
        grailsDomainBinder.bindMapSecondPass(property, mappings, persistentClasses,
                (org.hibernate.mapping.Map) collection, sessionFactoryBeanName);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void doSecondPass(Map persistentClasses) throws MappingException {
        grailsDomainBinder.bindMapSecondPass(property, mappings, persistentClasses,
                (org.hibernate.mapping.Map) collection, sessionFactoryBeanName);
    }
}
