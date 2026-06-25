/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.orm.hibernate.cfg.domainbinding.util;

import java.util.Map;

import org.hibernate.MappingException;
import org.hibernate.boot.spi.InFlightMetadataCollector;

import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Basic;
import org.grails.orm.hibernate.cfg.JoinTable;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.PropertyConfig;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;

import static org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder.UNDERSCORE;

@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class TableForManyCalculator {

    private final PersistentEntityNamingStrategy namingStrategy;
    private final InFlightMetadataCollector mappings;
    private final BackticksRemover backticksRemover;

    public TableForManyCalculator(PersistentEntityNamingStrategy namingStrategy, InFlightMetadataCollector mappings) {
        this.namingStrategy = namingStrategy;
        this.mappings = mappings;
        this.backticksRemover = new BackticksRemover();
    }

    protected TableForManyCalculator(PersistentEntityNamingStrategy namingStrategy, InFlightMetadataCollector mappings, BackticksRemover backticksRemover) {
        this.namingStrategy = namingStrategy;
        this.mappings = mappings;
        this.backticksRemover = backticksRemover;
    }

    public String getTableName(HibernateToManyProperty property) {
        PropertyConfig config = property.getHibernateMappedForm();
        JoinTable joinTable = config.getJoinTable();

        String logicalName = calculateTableForMany(property);
        return (joinTable != null && joinTable.getName() != null) ?
                joinTable.getName() : namingStrategy.resolveTableName(logicalName);
    }

    public String getJoinTableSchema(HibernateToManyProperty property) {
        PropertyConfig config = property.getHibernateMappedForm();
        JoinTable joinTable = config.getJoinTable();
        String owningTableSchema = property.getTable().getSchema();

        if (joinTable != null && joinTable.getSchema() != null) {
            return joinTable.getSchema();
        }
        String schemaName = NamespaceNameExtractor.getSchemaName(mappings);
        return (schemaName == null) ? owningTableSchema : schemaName;
    }

    public String getJoinTableCatalog(HibernateToManyProperty property) {
        PropertyConfig config = property.getHibernateMappedForm();
        JoinTable joinTable = config.getJoinTable();

        if (joinTable != null && joinTable.getCatalog() != null) {
            return joinTable.getCatalog();
        }
        return NamespaceNameExtractor.getCatalogName(mappings);
    }

    /**
     * Calculates the mapping table for a many-to-many. One side of the relationship has to "own" the
     * relationship so that there is not a situation where you have two mapping tables for left_right
     * and right_left
     */
    public String calculateTableForMany(HibernatePersistentProperty property) {
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

        // Only proceed with association logic if it's an actual Association and has an associated
        // entity
        //TODO Use Hibernate hierarchy
        if (!(property instanceof Association<?> association)) {
            throw new MappingException("Property [" + property.getName() +
                    "] is not an association and is not a basic type for table calculation.");
        }

        GrailsHibernatePersistentEntity domainClass =
                (GrailsHibernatePersistentEntity) association.getAssociatedEntity();
        if (domainClass == null) {
            throw new MappingException(
                    "Expected an entity to be associated with the association (" + property + ") and none was found. ");
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
