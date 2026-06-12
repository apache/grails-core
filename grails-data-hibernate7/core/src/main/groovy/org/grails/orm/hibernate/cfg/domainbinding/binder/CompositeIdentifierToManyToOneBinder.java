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
package org.grails.orm.hibernate.cfg.domainbinding.binder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.DependantValue;
import org.hibernate.mapping.KeyValue;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.mapping.ToOne;

import org.grails.orm.hibernate.cfg.ColumnConfig;
import org.grails.orm.hibernate.cfg.HibernateCompositeIdentity;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToOneProperty;
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover;
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher;
import org.grails.orm.hibernate.cfg.domainbinding.util.ForeignKeyColumnCountCalculator;

import static org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder.UNDERSCORE;

@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class CompositeIdentifierToManyToOneBinder {

    private final ForeignKeyColumnCountCalculator foreignKeyColumnCountCalculator;
    private final PersistentEntityNamingStrategy namingStrategy;
    private final DefaultColumnNameFetcher defaultColumnNameFetcher;
    private final BackticksRemover backticksRemover;
    private final SimpleValueBinder simpleValueBinder;

    public CompositeIdentifierToManyToOneBinder(
            ForeignKeyColumnCountCalculator foreignKeyColumnCountCalculator,
            PersistentEntityNamingStrategy namingStrategy,
            DefaultColumnNameFetcher defaultColumnNameFetcher,
            BackticksRemover backticksRemover,
            SimpleValueBinder simpleValueBinder) {
        this.foreignKeyColumnCountCalculator = foreignKeyColumnCountCalculator;
        this.namingStrategy = namingStrategy;
        this.defaultColumnNameFetcher = defaultColumnNameFetcher;
        this.backticksRemover = backticksRemover;
        this.simpleValueBinder = simpleValueBinder;
    }

    public CompositeIdentifierToManyToOneBinder(
            MetadataBuildingContext metadataBuildingContext,
            PersistentEntityNamingStrategy namingStrategy,
            JdbcEnvironment jdbcEnvironment) {
        this(
                new ForeignKeyColumnCountCalculator(),
                namingStrategy,
                new DefaultColumnNameFetcher(namingStrategy),
                new BackticksRemover(),
                new SimpleValueBinder(metadataBuildingContext, namingStrategy, jdbcEnvironment));
    }

    public void bindCompositeIdentifierToManyToOne(
            HibernatePersistentProperty property,
            SimpleValue value,
            HibernateCompositeIdentity compositeId,
            GrailsHibernatePersistentEntity refDomainClass,
            String path) {
        String[] propertyNames = compositeId.getPropertyNames();
        List<ColumnConfig> columns = property.getHibernateMappedForm().getColumns();
        int existingCount = columns.size();
        if (existingCount !=
                foreignKeyColumnCountCalculator.calculateForeignKeyColumnCount(refDomainClass, propertyNames)) {
            String prefix = refDomainClass.getTableName(namingStrategy);
            IntStream.range(0, propertyNames.length)
                    .boxed()
                    .flatMap(idx -> {
                        ColumnConfig cc = idx < existingCount ? columns.get(idx) : new ColumnConfig();
                        if (cc.getName() != null) {
                            return Stream.empty();
                        }
                        String propertyName = propertyNames[idx];
                        HibernatePersistentProperty ref = refDomainClass.getHibernatePropertyByName(propertyName);
                        return tryExpandNestedComposite(prefix, propertyName, ref)
                                .orElseGet(() -> singleColumn(prefix, propertyName, ref, cc));
                    })
                    .forEach(columns::add);
        }
        simpleValueBinder.bindSimpleValue(property, null, value, path);
        KeyValue referencedIdentifier = getReferencedIdentifier(refDomainClass);
        int[] originalOrder = sortReferencedCompositeIdentifier(referencedIdentifier);
        sortOrIndexColumns(value, originalOrder);
        if (referencedIdentifier != null && value.createForeignKeyOfEntity(
                refDomainClass.getName(), getReferencedColumns(propertyNames, referencedIdentifier, originalOrder)) != null) {
            value.disableForeignKey();
        }
        if (value instanceof ToOne toOne) {
            toOne.setSorted(true);
        }
        else if (value instanceof DependantValue dependantValue) {
            dependantValue.setSorted(true);
        }
    }

    /**
     * If {@code ref} is a to-one whose associated entity has a composite identity, returns a stream
     * of one named {@link ColumnConfig} per composite-identity property. Returns empty otherwise.
     */
    private Optional<Stream<ColumnConfig>> tryExpandNestedComposite(
            String prefix, String propertyName, HibernatePersistentProperty ref) {
        if (!(ref instanceof HibernateToOneProperty toOne)) {
            return Optional.empty();
        }
        HibernatePersistentProperty[] nestedComposite =
                toOne.getHibernateAssociatedEntity().getCompositeIdentity();
        if (nestedComposite == null) {
            return Optional.empty();
        }
        return Optional.of(Arrays.stream(nestedComposite)
                .map(cip -> namedColumn(join(
                        prefix,
                        namingStrategy.resolveColumnName(propertyName),
                        defaultColumnNameFetcher.getDefaultColumnName(cip)))));
    }

    private Stream<ColumnConfig> singleColumn(
            String prefix, String propertyName, HibernatePersistentProperty ref, ColumnConfig cc) {
        String suffix = ref != null ? defaultColumnNameFetcher.getDefaultColumnName(ref) : propertyName;
        cc.setName(join(prefix, suffix));
        return Stream.of(cc);
    }

    private ColumnConfig namedColumn(String name) {
        ColumnConfig cc = new ColumnConfig();
        cc.setName(name);
        return cc;
    }

    private KeyValue getReferencedIdentifier(GrailsHibernatePersistentEntity refDomainClass) {
        PersistentClass persistentClass = refDomainClass.getPersistentClass();
        return persistentClass != null ? persistentClass.getIdentifier() : null;
    }

    private int[] sortReferencedCompositeIdentifier(KeyValue identifier) {
        return identifier instanceof Component component ? component.sortProperties() : null;
    }

    private void sortOrIndexColumns(SimpleValue value, int[] originalOrder) {
        if (originalOrder != null) {
            value.sortColumns(originalOrder);
            return;
        }
        List<Column> columns = value.getColumns();
        for (int i = 0; i < columns.size(); i++) {
            columns.get(i).setTypeIndex(i);
        }
    }

    private List<Column> getReferencedColumns(
            String[] propertyNames, KeyValue identifier, int[] originalOrder) {
        if (!(identifier instanceof Component component)) {
            return identifier.getColumns();
        }
        List<Column> referencedColumns = Arrays.stream(propertyNames)
                .flatMap(propertyName -> component.getProperty(propertyName).getValue().getColumns().stream())
                .collect(Collectors.toCollection(ArrayList::new));
        return originalOrder != null ? sortedColumns(referencedColumns, originalOrder) : referencedColumns;
    }

    private List<Column> sortedColumns(List<Column> columns, int[] originalOrder) {
        List<Column> sortedColumns = new ArrayList<>(columns);
        for (int i = 0; i < originalOrder.length; i++) {
            sortedColumns.set(originalOrder[i], columns.get(i));
        }
        return sortedColumns;
    }

    private String join(String... parts) {
        return Arrays.stream(parts).map(backticksRemover).collect(Collectors.joining(String.valueOf(UNDERSCORE)));
    }
}
