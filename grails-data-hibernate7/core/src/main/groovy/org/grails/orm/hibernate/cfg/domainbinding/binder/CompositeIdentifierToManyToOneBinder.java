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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.mapping.SimpleValue;

import org.grails.orm.hibernate.cfg.ColumnConfig;
import org.grails.orm.hibernate.cfg.CompositeIdentity;
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
            CompositeIdentity compositeId,
            GrailsHibernatePersistentEntity refDomainClass,
            String path) {
        String[] propertyNames = compositeId.getPropertyNames();
        List<ColumnConfig> columns = property.getHibernateMappedForm().getColumns();
        int existingCount = columns.size();
        if (existingCount
                != foreignKeyColumnCountCalculator.calculateForeignKeyColumnCount(refDomainClass, propertyNames)) {
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

    private String join(String... parts) {
        return Arrays.stream(parts).map(backticksRemover).collect(Collectors.joining(String.valueOf(UNDERSCORE)));
    }
}
