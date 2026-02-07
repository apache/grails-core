package org.grails.orm.hibernate.cfg.domainbinding.secondpass;

import org.hibernate.mapping.Collection;

public interface GrailsSecondPass {

    default void createCollectionKeys(Collection collection) {
        collection.createAllKeys();
    }
}
