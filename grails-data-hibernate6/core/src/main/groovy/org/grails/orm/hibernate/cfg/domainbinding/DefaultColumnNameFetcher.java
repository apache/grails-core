package org.grails.orm.hibernate.cfg.domainbinding;

import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Basic;
import org.grails.datastore.mapping.model.types.ManyToMany;

public class DefaultColumnNameFetcher {

    private static final String FOREIGN_KEY_SUFFIX = "_id";
    private static final String UNDERSCORE = "_";

    private final NamingStrategyWrapper namingStrategyWrapper;
    private final BackticksRemover backticksRemover;

    public DefaultColumnNameFetcher( NamingStrategyWrapper namingStrategyWrapper) {
        this.namingStrategyWrapper = namingStrategyWrapper;
        this.backticksRemover = new BackticksRemover();
    }

    protected DefaultColumnNameFetcher(NamingStrategyWrapper namingStrategyWrapper , BackticksRemover backticksRemover) {
        this.namingStrategyWrapper = namingStrategyWrapper;
        this.backticksRemover = backticksRemover;
    }

    public String getDefaultColumnName(PersistentProperty property) {

        String columnName = namingStrategyWrapper.getColumnName(property.getName());
        if (property instanceof Association) {
            Association association = (Association) property;
            boolean isBasic = property instanceof Basic;
            if (isBasic && (new PersistentPropertyToPropertyConfig().apply(property)).getType() != null) {
                return columnName;
            }

            if (isBasic) {
                return namingStrategyWrapper.getForeignKeyForPropertyDomainClass(property);
            }

            if (property instanceof ManyToMany) {
                return namingStrategyWrapper.getForeignKeyForPropertyDomainClass(property);
            }

            if (!association.isBidirectional() && association instanceof org.grails.datastore.mapping.model.types.OneToMany) {
                String prefix = namingStrategyWrapper.getTableName(property.getOwner().getName());
                return backticksRemover.apply(prefix) + UNDERSCORE + backticksRemover.apply(columnName) + FOREIGN_KEY_SUFFIX;
            }

            if (property.isInherited() && property.isBidirectionalManyToOne()) {
                return namingStrategyWrapper.getColumnName(property.getOwner().getName()) + '_' + columnName + FOREIGN_KEY_SUFFIX;
            }

            return columnName + FOREIGN_KEY_SUFFIX;
        }


        return columnName;
    }
}
