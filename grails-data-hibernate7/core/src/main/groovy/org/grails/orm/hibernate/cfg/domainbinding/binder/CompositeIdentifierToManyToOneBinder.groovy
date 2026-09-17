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
package org.grails.orm.hibernate.cfg.domainbinding.binder

import groovy.transform.CompileStatic
import org.hibernate.boot.spi.MetadataBuildingContext
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment
import org.hibernate.mapping.Column
import org.hibernate.mapping.SimpleValue

import org.grails.orm.hibernate.cfg.ColumnConfig
import org.grails.orm.hibernate.cfg.HibernateCompositeIdentity
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToOneProperty
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.ForeignKeyColumnCountCalculator

import static org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder.UNDERSCORE

@CompileStatic
@SuppressWarnings('PMD.DataflowAnomalyAnalysis')
class CompositeIdentifierToManyToOneBinder {

    private final ForeignKeyColumnCountCalculator foreignKeyColumnCountCalculator
    private final PersistentEntityNamingStrategy namingStrategy
    private final DefaultColumnNameFetcher defaultColumnNameFetcher
    private final BackticksRemover backticksRemover
    private final SimpleValueBinder simpleValueBinder

    CompositeIdentifierToManyToOneBinder(
            ForeignKeyColumnCountCalculator foreignKeyColumnCountCalculator,
            PersistentEntityNamingStrategy namingStrategy,
            DefaultColumnNameFetcher defaultColumnNameFetcher,
            BackticksRemover backticksRemover,
            SimpleValueBinder simpleValueBinder) {
        this.foreignKeyColumnCountCalculator = foreignKeyColumnCountCalculator
        this.namingStrategy = namingStrategy
        this.defaultColumnNameFetcher = defaultColumnNameFetcher
        this.backticksRemover = backticksRemover
        this.simpleValueBinder = simpleValueBinder
    }

    CompositeIdentifierToManyToOneBinder(
            MetadataBuildingContext metadataBuildingContext,
            PersistentEntityNamingStrategy namingStrategy,
            JdbcEnvironment jdbcEnvironment) {
        this(
                new ForeignKeyColumnCountCalculator(),
                namingStrategy,
                new DefaultColumnNameFetcher(namingStrategy),
                new BackticksRemover(),
                new SimpleValueBinder(metadataBuildingContext, namingStrategy, jdbcEnvironment))
    }

    void bindCompositeIdentifierToManyToOne(
            HibernatePersistentProperty property,
            SimpleValue value,
            HibernateCompositeIdentity compositeId,
            GrailsHibernatePersistentEntity refDomainClass,
            String path) {
        String[] propertyNames = compositeId.propertyNames
        List<ColumnConfig> columns = property.hibernateMappedForm.columns
        int existingCount = columns.size()
        if (existingCount !=
                foreignKeyColumnCountCalculator.calculateForeignKeyColumnCount(refDomainClass, propertyNames)) {
            String prefix = refDomainClass.getTableName(namingStrategy)
            List<ColumnConfig> generated = []
            for (int idx = 0; idx < propertyNames.length; idx++) {
                ColumnConfig cc = idx < existingCount ? columns.get(idx) : new ColumnConfig()
                if (cc.name != null) {
                    continue
                }
                String propertyName = propertyNames[idx]
                HibernatePersistentProperty ref = refDomainClass.getHibernatePropertyByName(propertyName)
                List<ColumnConfig> nested = tryExpandNestedComposite(prefix, propertyName, ref)
                if (nested != null) {
                    generated.addAll(nested)
                } else {
                    generated.add(singleColumn(prefix, propertyName, ref, cc))
                }
            }
            columns.addAll(generated)
        }
        simpleValueBinder.bindSimpleValue(property, null, value, path)
        refDomainClass.sortOrIndexForeignKeyColumns(value)
        List<Column> referencedColumns = refDomainClass.getReferencedIdentifierColumns(propertyNames)
        if (!referencedColumns.isEmpty() &&
                value.createForeignKeyOfEntity(refDomainClass.name, referencedColumns) != null) {
            value.disableForeignKey()
        }
        property.markValueSorted(value)
    }

    /**
     * If {@code ref} is a to-one whose associated entity has a composite identity, returns a list
     * of one named {@link ColumnConfig} per composite-identity property. Returns {@code null} otherwise.
     */
    private List<ColumnConfig> tryExpandNestedComposite(
            String prefix, String propertyName, HibernatePersistentProperty ref) {
        if (!(ref instanceof HibernateToOneProperty)) {
            return null
        }
        HibernateToOneProperty toOne = (HibernateToOneProperty) ref
        HibernatePersistentProperty[] nestedComposite =
                toOne.hibernateAssociatedEntity.compositeIdentity
        if (nestedComposite == null) {
            return null
        }
        List<ColumnConfig> result = []
        for (HibernatePersistentProperty cip : nestedComposite) {
            result.add(namedColumn(join(
                    prefix,
                    namingStrategy.resolveColumnName(propertyName),
                    defaultColumnNameFetcher.getDefaultColumnName(cip))))
        }
        return result
    }

    private ColumnConfig singleColumn(
            String prefix, String propertyName, HibernatePersistentProperty ref, ColumnConfig cc) {
        String suffix = ref != null ? defaultColumnNameFetcher.getDefaultColumnName(ref) : propertyName
        cc.name = join(prefix, suffix)
        return cc
    }

    private ColumnConfig namedColumn(String name) {
        ColumnConfig cc = new ColumnConfig()
        cc.name = name
        return cc
    }

    private String join(String... parts) {
        List<String> cleaned = []
        for (String part : parts) {
            cleaned.add(backticksRemover.apply(part))
        }
        return cleaned.join(UNDERSCORE.toString())
    }

}
