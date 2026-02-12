package org.grails.orm.hibernate.cfg.domainbinding.binder;

import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.mapping.RootClass;

import org.grails.orm.hibernate.cfg.CompositeIdentity;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateIdentity;
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

        HibernateIdentity id = gormMapping != null ? gormMapping.getIdentity() : null;
        if (id instanceof CompositeIdentity || (id == null && domainClass.getCompositeIdentity() != null)) {
            compositeIdBinder.bindCompositeId(domainClass, root, (CompositeIdentity) id, mappings, sessionFactoryBeanName);
        } else {
            Identity identity = id instanceof Identity ? (Identity) id : null;
            if (identity != null && identity.getName() == null) {
                identity.setName(root.getEntityName());
            }
            simpleIdBinder.bindSimpleId(domainClass, root, identity);
        }
    }
}