package org.grails.orm.hibernate.cfg.domainbinding;

import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.orm.hibernate.cfg.ColumnConfig;
import org.grails.orm.hibernate.cfg.GrailsHibernateUtil;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.PropertyConfig;

public class ColumnNameForPropertyAndPathFetcher {

    private final PersistentEntityNamingStrategy namingStrategy;
    private final HibernateEntityWrapper hibernateEntityWrapper;
    private final PersistentPropertyToPropertyConfig persistentPropertyToPropertyConfig;
    private final DefaultColumnNameFetcher defaultColumnNameFetcher;
    private final BackticksRemover backticksRemover;

    public ColumnNameForPropertyAndPathFetcher(PersistentEntityNamingStrategy namingStrategy
    ) {
        this.namingStrategy = namingStrategy;
        this.hibernateEntityWrapper = new HibernateEntityWrapper();
        this.persistentPropertyToPropertyConfig = new PersistentPropertyToPropertyConfig();
        this.defaultColumnNameFetcher = new DefaultColumnNameFetcher(namingStrategy);
        this.backticksRemover = new BackticksRemover();
    }

    protected ColumnNameForPropertyAndPathFetcher(PersistentEntityNamingStrategy namingStrategy
            , HibernateEntityWrapper hibernateEntityWrapper
            , PersistentPropertyToPropertyConfig persistentPropertyToPropertyConfig
    , DefaultColumnNameFetcher defaultColumnNameFetcher
    , BackticksRemover backticksRemover) {
        this.namingStrategy = namingStrategy;
        this.hibernateEntityWrapper = hibernateEntityWrapper;
        this.persistentPropertyToPropertyConfig = persistentPropertyToPropertyConfig;
        this.defaultColumnNameFetcher = defaultColumnNameFetcher;
        this.backticksRemover = backticksRemover;

    }


    private static final String UNDERSCORE = "_";

    public String getColumnNameForPropertyAndPath(PersistentProperty grailsProp,
                                                   String path, ColumnConfig cc) {
        // First try the column config.
        String columnName = null;
        if (cc == null) {
            // No column config given, attempt to obtain the property config directly from the property
            PropertyConfig c = null;
            try {
                c = persistentPropertyToPropertyConfig.apply(grailsProp);
            } catch (Exception ignore) {
                // If we cannot resolve a PropertyConfig, treat as absent and fall back later
            }

            if (grailsProp.supportsJoinColumnMapping() && c != null && c.hasJoinKeyMapping()) {
                columnName = c.getJoinTable().getKey().getName();
            }
            else if (c != null && c.getColumn() != null) {
                columnName = c.getColumn();
            }
        }
        else {
            if (grailsProp.supportsJoinColumnMapping()) {
                PropertyConfig pc = persistentPropertyToPropertyConfig.apply(grailsProp);
                if (pc.hasJoinKeyMapping()) {
                    columnName = pc.getJoinTable().getKey().getName();
                }
                else {
                    columnName = cc.getName();
                }
            }
            else {
                columnName = cc.getName();
            }
        }

        if (columnName == null) {
            if (GrailsHibernateUtil.isNotEmpty(path)) {
                String s1 = namingStrategy.resolveColumnName(path);

                String s2 = defaultColumnNameFetcher.getDefaultColumnName(grailsProp);
                columnName = backticksRemover.apply(s1) + UNDERSCORE + backticksRemover.apply(s2);
            } else {

                columnName = defaultColumnNameFetcher.getDefaultColumnName(grailsProp);
            }
        }
        return columnName;
    }
}
