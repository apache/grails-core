package org.grails.orm.hibernate.cfg;

import org.grails.datastore.mapping.model.PersistentProperty;

/**
 * Allows plugging into to custom naming strategies
 *
 * @author Graeme Rocher
 * @since 5.0
 */
public interface PersistentEntityNamingStrategy {

    String resolveColumnName(String logicalName);

    default String resolveTableName(GrailsHibernatePersistentEntity entity){
        return resolveTableName(entity.getJavaClass().getSimpleName());
    }

    String resolveTableName(String logicalName);

    String resolveForeignKeyForPropertyDomainClass(PersistentProperty property);
}
