package org.grails.orm.hibernate.cfg.domainbinding.binder;

import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.mapping.RootClass;

import org.grails.orm.hibernate.cfg.CompositeIdentity;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateIdentity;
import org.grails.orm.hibernate.cfg.Identity;
import org.grails.orm.hibernate.cfg.Mapping;

import jakarta.annotation.Nonnull;

public class IdentityBinder {

    private final SimpleIdBinder simpleIdBinder;
    private final CompositeIdBinder compositeIdBinder;

    public IdentityBinder(SimpleIdBinder simpleIdBinder,
                          CompositeIdBinder compositeIdBinder) {
        this.simpleIdBinder = simpleIdBinder;
        this.compositeIdBinder = compositeIdBinder;
    }

    public void bindIdentity(
            @Nonnull GrailsHibernatePersistentEntity domainClass,
            RootClass root,
            @Nonnull InFlightMetadataCollector mappings,
            Mapping gormMapping) {

        HibernateIdentity id = gormMapping != null ? gormMapping.getIdentity() : null;
        if (id instanceof CompositeIdentity || (id == null && domainClass.getCompositeIdentity() != null)) {
            compositeIdBinder.bindCompositeId(domainClass, root, (CompositeIdentity) id, mappings);
        } else {
            Identity identity = id instanceof Identity ? (Identity) id : null;
            if (identity != null && identity.getName() == null) {
                identity.setName(root.getEntityName());
            }
            simpleIdBinder.bindSimpleId(domainClass, root, identity, root.getTable());
        }
    }
}