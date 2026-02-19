package org.grails.orm.hibernate.cfg.domainbinding.util;

import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Basic;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToManyProperty;

public class DefaultColumnNameFetcher {

    private static final String FOREIGN_KEY_SUFFIX = "_id";
    private static final String UNDERSCORE = "_";

    private final PersistentEntityNamingStrategy namingStrategyWrapper;
    private final BackticksRemover backticksRemover;

    public DefaultColumnNameFetcher( PersistentEntityNamingStrategy namingStrategyWrapper) {
        this.namingStrategyWrapper = namingStrategyWrapper;
        this.backticksRemover = new BackticksRemover();
    }

    public DefaultColumnNameFetcher(PersistentEntityNamingStrategy namingStrategyWrapper , BackticksRemover backticksRemover) {
        this.namingStrategyWrapper = namingStrategyWrapper;
        this.backticksRemover = backticksRemover;
    }

    public String getDefaultColumnName(GrailsHibernatePersistentProperty property) {

        String columnName = namingStrategyWrapper.resolveColumnName(property.getName());
        if (property instanceof Association) {
            Association association = (Association) property;
            boolean isBasic = property instanceof Basic;
            if (isBasic && (property.getMappedForm()).getType() != null) {
                return columnName;
            }

            if (isBasic) {
                return namingStrategyWrapper.resolveForeignKeyForPropertyDomainClass(property);
            }

            if (property instanceof HibernateManyToManyProperty) {
                return namingStrategyWrapper.resolveForeignKeyForPropertyDomainClass(property);
            }

            if (!association.isBidirectional() && association instanceof HibernateOneToManyProperty) {
                String prefix = namingStrategyWrapper.resolveTableName(property.getOwner().getRootEntity().getJavaClass().getSimpleName());
                return backticksRemover.apply(prefix) + UNDERSCORE + backticksRemover.apply(columnName) + FOREIGN_KEY_SUFFIX;
            }

            if (property.isInherited() && property.isBidirectionalManyToOne()) {
                return namingStrategyWrapper.resolveColumnName(property.getOwner().getRootEntity().getJavaClass().getSimpleName()) + '_' + columnName + FOREIGN_KEY_SUFFIX;
            }

            return columnName + FOREIGN_KEY_SUFFIX;
        }


        return columnName;
    }
}
