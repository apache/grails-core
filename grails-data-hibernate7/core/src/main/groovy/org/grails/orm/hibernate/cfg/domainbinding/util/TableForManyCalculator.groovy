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
package org.grails.orm.hibernate.cfg.domainbinding.util

import groovy.transform.CompileStatic
import org.hibernate.MappingException
import org.hibernate.boot.spi.InFlightMetadataCollector

import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.Basic
import org.grails.orm.hibernate.cfg.JoinTable
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToManyProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty

import static org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder.UNDERSCORE

@CompileStatic
@SuppressWarnings('PMD.DataflowAnomalyAnalysis')
class TableForManyCalculator {

    private final PersistentEntityNamingStrategy namingStrategy
    private final InFlightMetadataCollector mappings
    private final BackticksRemover backticksRemover

    TableForManyCalculator(PersistentEntityNamingStrategy namingStrategy, InFlightMetadataCollector mappings) {
        this.namingStrategy = namingStrategy
        this.mappings = mappings
        this.backticksRemover = new BackticksRemover()
    }

    protected TableForManyCalculator(PersistentEntityNamingStrategy namingStrategy, InFlightMetadataCollector mappings, BackticksRemover backticksRemover) {
        this.namingStrategy = namingStrategy
        this.mappings = mappings
        this.backticksRemover = backticksRemover
    }

    String getTableName(HibernateToManyProperty property) {
        PropertyConfig config = property.hibernateMappedForm
        JoinTable joinTable = config.joinTable

        String logicalName = calculateTableForMany(property)
        return (joinTable != null && joinTable.name != null) ?
                joinTable.name : namingStrategy.resolveTableName(logicalName)
    }

    String getJoinTableSchema(HibernateToManyProperty property) {
        PropertyConfig config = property.hibernateMappedForm
        JoinTable joinTable = config.joinTable

        if (joinTable != null && joinTable.schema != null) {
            return joinTable.schema
        }
        String schemaName = NamespaceNameExtractor.getSchemaName(mappings)
        // Ask the owning entity directly: property.getTable() may already return the
        // (partially built) collection table for a basic collection element at this point.
        return (schemaName == null) ? property.persistentClass.table.schema : schemaName
    }

    String getJoinTableCatalog(HibernateToManyProperty property) {
        PropertyConfig config = property.hibernateMappedForm
        JoinTable joinTable = config.joinTable

        if (joinTable != null && joinTable.catalog != null) {
            return joinTable.catalog
        }
        return NamespaceNameExtractor.getCatalogName(mappings)
    }

    /**
     * Calculates the mapping table for a many-to-many. One side of the relationship has to "own" the
     * relationship so that there is not a situation where you have two mapping tables for left_right
     * and right_left
     */
    String calculateTableForMany(HibernatePersistentProperty property) {
        String propertyColumnName = namingStrategy.resolveColumnName(property.name)
        PropertyConfig config = property.mappedForm
        JoinTable jt = config.joinTable
        boolean hasJoinTableMapping = jt != null && jt.name != null
        GrailsHibernatePersistentEntity domainClass1 = property.hibernateOwner
        String left = domainClass1.getTableName(namingStrategy)

        if (Map.isAssignableFrom(property.type)) {
            if (hasJoinTableMapping) {
                return jt.name
            }
            return backticksRemover.apply(left) + UNDERSCORE.toString() + backticksRemover.apply(propertyColumnName)
        }
        else if (property instanceof Basic) {
            if (hasJoinTableMapping) {
                return jt.name
            }
            return backticksRemover.apply(left) + UNDERSCORE.toString() + backticksRemover.apply(propertyColumnName)
        }

        // Only proceed with association logic if it's an actual Association and has an associated
        // entity
        if (!(property instanceof Association<?>)) {
            throw new MappingException("Property [${property.name}] is not an association and is not a basic type for table calculation.".toString())
        }
        Association<?> association = (Association<?>) property

        GrailsHibernatePersistentEntity domainClass =
                (GrailsHibernatePersistentEntity) association.associatedEntity
        if (domainClass == null) {
            throw new MappingException(
                    "Expected an entity to be associated with the association (${property}) and none was found. ".toString())
        }
        String right = domainClass.getTableName(namingStrategy)

        if (property instanceof HibernateManyToManyProperty) {
            HibernateManyToManyProperty property1 = (HibernateManyToManyProperty) property
            if (hasJoinTableMapping) {
                return jt.name
            }
            if (association.isOwningSide()) {
                return backticksRemover.apply(left) + UNDERSCORE.toString() + backticksRemover.apply(propertyColumnName)
            }
            String s2 = namingStrategy.resolveColumnName(property1.inversePropertyName)
            return backticksRemover.apply(right) + UNDERSCORE.toString() + backticksRemover.apply(s2)
        }

        if (property.supportsJoinColumnMapping()) {
            if (hasJoinTableMapping) {
                return jt.name
            }
            return backticksRemover.apply(left) + UNDERSCORE.toString() + backticksRemover.apply(right)
        }

        if (association.isOwningSide()) {
            return backticksRemover.apply(left) + UNDERSCORE.toString() + backticksRemover.apply(right)
        }
        return backticksRemover.apply(right) + UNDERSCORE.toString() + backticksRemover.apply(left)
    }

}
