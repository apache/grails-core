package org.grails.orm.hibernate.cfg.domainbinding;

import java.util.Optional;

import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;

/**
 * Evaluates the table name for the given property
 *
 */
public class TableNameFetcher {

    private final PersistentEntityNamingStrategy persistentEntityNamingStrategy;
    private final HibernateEntityWrapper hibernateEntityWrapper;

    public TableNameFetcher(PersistentEntityNamingStrategy persistentEntityNamingStrategy) {
        this.persistentEntityNamingStrategy = persistentEntityNamingStrategy;
        this.hibernateEntityWrapper = new HibernateEntityWrapper();
    }

    protected TableNameFetcher(PersistentEntityNamingStrategy persistentEntityNamingStrategy, HibernateEntityWrapper hibernateEntityWrapper) {
        this.persistentEntityNamingStrategy = persistentEntityNamingStrategy;
        this.hibernateEntityWrapper = hibernateEntityWrapper;
    }

    public String getTableName(PersistentEntity domainClass) {
        var tableName = hibernateEntityWrapper.getMappedForm(domainClass).getTableName();
        return tableName != null ? tableName  :persistentEntityNamingStrategy.resolveTableName(domainClass);
    }
}
