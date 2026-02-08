package org.grails.orm.hibernate.cfg.domainbinding;

import java.util.Map;

import org.hibernate.MappingException;

import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Basic;
import org.grails.datastore.mapping.model.types.ManyToMany;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.JoinTable;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.PropertyConfig;

import static org.grails.orm.hibernate.cfg.GrailsDomainBinder.UNDERSCORE;

public class TableForManyCalculator {

    private final PersistentEntityNamingStrategy namingStrategy;
    private final TableNameFetcher tableNameFetcher;
    private final BackticksRemover backticksRemover;
    private final BackTigsTrimmer backTigsTrimmer;

    public TableForManyCalculator(PersistentEntityNamingStrategy namingStrategy) {
        this.namingStrategy = namingStrategy;
        tableNameFetcher = new TableNameFetcher(namingStrategy);
        backticksRemover = new BackticksRemover();
        backTigsTrimmer = new BackTigsTrimmer();
    }

    protected TableForManyCalculator(PersistentEntityNamingStrategy namingStrategy
             , TableNameFetcher tableNameFetcher
            , BackticksRemover backticksRemover
    , BackTigsTrimmer backTigsTrimmer) {
        this.namingStrategy = namingStrategy;
        this.tableNameFetcher = tableNameFetcher;
        this.backticksRemover = backticksRemover;
        this.backTigsTrimmer = backTigsTrimmer;
    }



    /**
     * Calculates the mapping table for a many-to-many. One side of
     * the relationship has to "own" the relationship so that there is not a situation
     * where you have two mapping tables for left_right and right_left
     */
    public String calculateTableForMany(GrailsHibernatePersistentProperty property) {
        String propertyColumnName = namingStrategy.resolveColumnName(property.getName());
        PropertyConfig config = property.getMappedForm();
        JoinTable jt = config.getJoinTable();
        boolean hasJoinTableMapping = jt != null && jt.getName() != null;
        PersistentEntity domainClass1 = property.getOwner();
        String left = tableNameFetcher.getTableName((GrailsHibernatePersistentEntity) domainClass1);

        if (Map.class.isAssignableFrom(property.getType())) {
            if (hasJoinTableMapping) {
                return jt.getName();
            }
            return backticksRemover.apply(left) + UNDERSCORE + backticksRemover.apply(propertyColumnName);
        } else if (property instanceof Basic) {
            if (hasJoinTableMapping) {
                return jt.getName();
            }
            return backticksRemover.apply(left) + UNDERSCORE + backticksRemover.apply(propertyColumnName);
        }

        // Only proceed with association logic if it's an actual Association and has an associated entity
        if (!(property instanceof Association association)) {
            throw new MappingException("Property [" + property.getName() + "] is not an association and is not a basic type for table calculation.");
        }

        GrailsHibernatePersistentEntity domainClass = (GrailsHibernatePersistentEntity) association.getAssociatedEntity();
        if (domainClass == null) {
            throw new MappingException("Expected an entity to be associated with the association (" + property + ") and none was found. ");
        }
        String right = tableNameFetcher.getTableName(domainClass);

        if (property instanceof ManyToMany property1) {
            if (hasJoinTableMapping) {
                return jt.getName();
            }
            if (association.isOwningSide()) {
                return backticksRemover.apply(left) + UNDERSCORE + backticksRemover.apply(propertyColumnName);
            }
            String s2 = namingStrategy.resolveColumnName(property1.getInversePropertyName());
            return backticksRemover.apply(right) + UNDERSCORE + backticksRemover.apply(s2);
        }

        if (property.supportsJoinColumnMapping()) {
            if (hasJoinTableMapping) {
                return jt.getName();
            }
            left = backTigsTrimmer.trimBackTigs(left);
            right = backTigsTrimmer.trimBackTigs(right);
            return backticksRemover.apply(left) + UNDERSCORE + backticksRemover.apply(right);
        }

        if (association.isOwningSide()) {
            return backticksRemover.apply(left) + UNDERSCORE + backticksRemover.apply(right);
        }
        return backticksRemover.apply(right) + UNDERSCORE + backticksRemover.apply(left);
    }
}
