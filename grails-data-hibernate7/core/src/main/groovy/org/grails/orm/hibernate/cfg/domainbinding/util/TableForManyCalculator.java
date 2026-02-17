package org.grails.orm.hibernate.cfg.domainbinding.util;

import java.util.Map;

import org.hibernate.MappingException;

import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Basic;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.JoinTable;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.PropertyConfig;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToManyProperty;

import static org.grails.orm.hibernate.cfg.GrailsDomainBinder.UNDERSCORE;

public class TableForManyCalculator {

    private final PersistentEntityNamingStrategy namingStrategy;
    private final BackticksRemover backticksRemover;

    public TableForManyCalculator(PersistentEntityNamingStrategy namingStrategy) {
        this.namingStrategy = namingStrategy;
        backticksRemover = new BackticksRemover();
    }

    protected TableForManyCalculator(PersistentEntityNamingStrategy namingStrategy
            , BackticksRemover backticksRemover) {
        this.namingStrategy = namingStrategy;
        this.backticksRemover = backticksRemover;
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
        GrailsHibernatePersistentEntity domainClass1 = property.getHibernateOwner();
        String left = domainClass1.getTableName(namingStrategy);

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
        String right = domainClass.getTableName(namingStrategy);

        if (property instanceof HibernateManyToManyProperty property1) {
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
            return backticksRemover.apply(left) + UNDERSCORE + backticksRemover.apply(right);
        }

        if (association.isOwningSide()) {
            return backticksRemover.apply(left) + UNDERSCORE + backticksRemover.apply(right);
        }
        return backticksRemover.apply(right) + UNDERSCORE + backticksRemover.apply(left);
    }
}
