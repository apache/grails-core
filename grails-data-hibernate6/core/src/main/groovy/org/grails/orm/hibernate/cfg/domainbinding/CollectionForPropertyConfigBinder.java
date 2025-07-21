package org.grails.orm.hibernate.cfg.domainbinding;

import org.grails.orm.hibernate.cfg.PropertyConfig;
import org.hibernate.FetchMode;
import org.hibernate.mapping.Collection;

public class CollectionForPropertyConfigBinder {

    public void bindCollectionForPropertyConfig(Collection collection, PropertyConfig config) {
        if (config == null) {
            collection.setLazy(true);
            collection.setExtraLazy(false);
        } else {
            final FetchMode fetch = config.getFetchMode();
            if(!fetch.equals(FetchMode.JOIN)) {
                collection.setLazy(true);
            }
            final Boolean lazy = config.getLazy();
            if(lazy != null) {
                collection.setExtraLazy(lazy);
            }
        }
    }
}
