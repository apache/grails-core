package org.grails.orm.hibernate.cfg.domainbinding.secondpass;

import java.util.Map;

import jakarta.annotation.Nonnull;

import org.hibernate.MappingException;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.mapping.Collection;

import org.grails.orm.hibernate.cfg.GrailsDomainBinder;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.binder.CollectionBinder;

/**
 * Second pass class for grails relationships. This is required as all
 * persistent classes need to be loaded in the first pass and then relationships
 * established in the second pass compile
 *
 * @author Graeme
 */
public class SetSecondPass implements org.hibernate.boot.spi.SecondPass, GrailsSecondPass  {

    private static final long serialVersionUID = -5540526942092611348L;

    protected final GrailsDomainBinder grailsDomainBinder;
    protected final CollectionBinder collectionBinder;
    private final CollectionSecondPassBinder collectionSecondPassBinder;
    protected final HibernateToManyProperty property;
    protected final @Nonnull InFlightMetadataCollector mappings;
    protected final Collection collection;
    protected final String sessionFactoryBeanName;

    public SetSecondPass(GrailsDomainBinder grailsDomainBinder,
                         CollectionBinder collectionBinder,
                         CollectionSecondPassBinder collectionSecondPassBinder,
                         HibernateToManyProperty property,
                         @Nonnull InFlightMetadataCollector mappings,
                         Collection coll,
                         String sessionFactoryBeanName) {
        this.grailsDomainBinder = grailsDomainBinder;
        this.collectionBinder = collectionBinder;
        this.collectionSecondPassBinder = collectionSecondPassBinder;
        this.property = property;
        this.mappings = mappings;
        this.collection = coll;
        this.sessionFactoryBeanName = sessionFactoryBeanName;

    }


    @SuppressWarnings("rawtypes")
    public void doSecondPass(Map persistentClasses) throws MappingException {
        collectionSecondPassBinder.bindCollectionSecondPass(property, mappings, persistentClasses, collection, sessionFactoryBeanName);
        createCollectionKeys(collection);
    }
}
