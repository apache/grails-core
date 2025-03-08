package org.grails.orm.hibernate.cfg;

import org.grails.datastore.mapping.model.PersistentEntity;

/**
 * Allows plugging into to custom naming strategies
 *
 * @author Graeme Rocher
 * @since 5.0
 */
public interface PersistentEntityNamingStrategy {

    String resolveTableName(PersistentEntity entity);

}
