package org.grails.orm.hibernate.cfg.domainbinding;

import org.hibernate.MappingException;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.mapping.RootClass;

import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.orm.hibernate.cfg.CompositeIdentity;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.HibernateIdentity;
import org.grails.orm.hibernate.cfg.Identity;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;

import jakarta.annotation.Nonnull;

public class IdentityBinder {

    private final SimpleIdBinder simpleIdBinder;
    private final CompositeIdBinder compositeIdBinder;

    public IdentityBinder(MetadataBuildingContext metadataBuildingContext,
                          PersistentEntityNamingStrategy namingStrategy,
                          JdbcEnvironment jdbcEnvironment,
                          CompositeIdBinder compositeIdBinder) {
        this(new SimpleIdBinder(metadataBuildingContext, namingStrategy, jdbcEnvironment), compositeIdBinder);
    }

    public IdentityBinder(SimpleIdBinder simpleIdBinder, CompositeIdBinder compositeIdBinder) {
        this.simpleIdBinder = simpleIdBinder;
        this.compositeIdBinder = compositeIdBinder;
    }

    protected IdentityBinder() {
        this.simpleIdBinder = null;
        this.compositeIdBinder = null;
    }

    public void bindIdentity(
            @Nonnull GrailsHibernatePersistentEntity domainClass,
            RootClass root,
            @Nonnull InFlightMetadataCollector mappings,
            Mapping gormMapping,
            String sessionFactoryBeanName) {

        if (gormMapping != null) {
            HibernateIdentity id = gormMapping.getIdentity();
            if (id instanceof CompositeIdentity) {
                compositeIdBinder.bindCompositeId(domainClass, root, (CompositeIdentity) id, mappings, sessionFactoryBeanName);
            } else {
                final Identity identity = (Identity) id;
                PersistentProperty identifierProp = domainClass.getIdentity();
                String propertyName = identity.getName();
                if (propertyName != null && !propertyName.equals(domainClass.getName())) {
                    PersistentProperty namedIdentityProp = domainClass.getPropertyByName(propertyName);
                    if (namedIdentityProp == null) {
                        throw new MappingException("Mapping specifies an identifier property name that doesn't exist [" + propertyName + "]");
                    }
                    if (!namedIdentityProp.equals(identifierProp)) {
                        identifierProp = namedIdentityProp;
                    }
                }
                if (identity.getName() == null) {
                    identity.setName(root.getEntityName());
                }
                simpleIdBinder.bindSimpleId(identifierProp, root, identity);
            }
        } else {
            if (domainClass.getCompositeIdentity() != null) {
                compositeIdBinder.bindCompositeId(domainClass, root, null, mappings, sessionFactoryBeanName);
            } else {
                PersistentProperty identifierProp = domainClass.getIdentity();
                if (identifierProp != null) {
                    simpleIdBinder.bindSimpleId(identifierProp, root, null);
                }
            }
        }
    }
}