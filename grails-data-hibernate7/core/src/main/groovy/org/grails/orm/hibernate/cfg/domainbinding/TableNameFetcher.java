package org.grails.orm.hibernate.cfg.domainbinding;

import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;

/**
 * Evaluates the table name for the given property
 *
 */
public class TableNameFetcher {

    private final PersistentEntityNamingStrategy persistentEntityNamingStrategy;

    public TableNameFetcher(PersistentEntityNamingStrategy persistentEntityNamingStrategy) {
        this.persistentEntityNamingStrategy = persistentEntityNamingStrategy;
    }

    public String getTableName(GrailsHibernatePersistentEntity domainClass) {
        Mapping result = domainClass.getMappedForm();
        var tableName = result != null ? result.getTableName() : null;
        return tableName != null ? tableName  :persistentEntityNamingStrategy.resolveTableName(domainClass);
    }
}
