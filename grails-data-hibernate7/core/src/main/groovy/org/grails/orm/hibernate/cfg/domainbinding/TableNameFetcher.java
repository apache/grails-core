package org.grails.orm.hibernate.cfg.domainbinding;

import java.util.Optional;

import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.orm.hibernate.cfg.GrailsDomainBinder;
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

    public String getTableName(PersistentEntity domainClass) {
        var tableName = GrailsDomainBinder.getMapping(domainClass).getTableName();
        return tableName != null ? tableName  :persistentEntityNamingStrategy.resolveTableName(domainClass);
    }
}
