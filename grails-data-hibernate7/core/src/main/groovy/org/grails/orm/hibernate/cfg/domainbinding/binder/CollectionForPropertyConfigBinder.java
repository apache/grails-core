package org.grails.orm.hibernate.cfg.domainbinding.binder;

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;
import org.hibernate.FetchMode;
import org.hibernate.mapping.Collection;

import java.util.Optional;

import jakarta.annotation.Nonnull;

public class CollectionForPropertyConfigBinder {

    public void bindCollectionForPropertyConfig(@Nonnull Collection collection,@Nonnull HibernateToManyProperty property) {
        collection.setLazy(!FetchMode.JOIN.equals(property.getFetchMode()));
        Optional.ofNullable(property.getLazy()).ifPresent(collection::setExtraLazy);
    }
}
