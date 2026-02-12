package org.grails.orm.hibernate.cfg.domainbinding.util;

import org.grails.orm.hibernate.cfg.ColumnConfig;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.GrailsHibernateUtil;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;

import java.util.Optional;

public class ColumnNameForPropertyAndPathFetcher {

    private final PersistentEntityNamingStrategy namingStrategy;
    private final DefaultColumnNameFetcher defaultColumnNameFetcher;
    private final BackticksRemover backticksRemover;

    public ColumnNameForPropertyAndPathFetcher(PersistentEntityNamingStrategy namingStrategy
    ) {
        this.namingStrategy = namingStrategy;
        this.defaultColumnNameFetcher = new DefaultColumnNameFetcher(namingStrategy);
        this.backticksRemover = new BackticksRemover();
    }

    protected ColumnNameForPropertyAndPathFetcher(PersistentEntityNamingStrategy namingStrategy
    , DefaultColumnNameFetcher defaultColumnNameFetcher
    , BackticksRemover backticksRemover) {
        this.namingStrategy = namingStrategy;
        this.defaultColumnNameFetcher = defaultColumnNameFetcher;
        this.backticksRemover = backticksRemover;

    }


    private static final String UNDERSCORE = "_";

    public String getColumnNameForPropertyAndPath(GrailsHibernatePersistentProperty grailsProp,
                                                   String path,
                                                  ColumnConfig cc) {
        return Optional.ofNullable(grailsProp.getColumnName(cc))
                .orElseGet(() -> {
                    String suffix = defaultColumnNameFetcher.getDefaultColumnName(grailsProp);
                    return Optional.ofNullable(path)
                            .filter(GrailsHibernateUtil::isNotEmpty)
                            .map(p -> backticksRemover.apply(namingStrategy.resolveColumnName(p)) + UNDERSCORE + backticksRemover.apply(suffix))
                            .orElse(suffix);
                });
    }
}
