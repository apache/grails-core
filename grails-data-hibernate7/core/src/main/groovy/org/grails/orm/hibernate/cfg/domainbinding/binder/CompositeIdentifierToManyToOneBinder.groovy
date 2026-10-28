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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.mapping.ToOne;

import org.grails.orm.hibernate.cfg.ColumnConfig;
import org.grails.orm.hibernate.cfg.HibernateCompositeIdentity;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToOneProperty;
import org.grails.orm.hibernate.cfg.domainbinding.secondpass.CompositeForeignKeySecondPass;
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover;
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher;
import org.grails.orm.hibernate.cfg.domainbinding.util.ForeignKeyColumnCountCalculator;

import static org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder.UNDERSCORE;

@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class CompositeIdentifierToManyToOneBinder {

    private final InFlightMetadataCollector metadataCollector;
    private final ForeignKeyColumnCountCalculator foreignKeyColumnCountCalculator;
    private final PersistentEntityNamingStrategy namingStrategy;
    private final DefaultColumnNameFetcher defaultColumnNameFetcher;
    private final BackticksRemover backticksRemover;
    private final SimpleValueBinder simpleValueBinder;

    public CompositeIdentifierToManyToOneBinder(
            InFlightMetadataCollector metadataCollector,
            ForeignKeyColumnCountCalculator foreignKeyColumnCountCalculator,
            PersistentEntityNamingStrategy namingStrategy,
            DefaultColumnNameFetcher defaultColumnNameFetcher,
            BackticksRemover backticksRemover,
            SimpleValueBinder simpleValueBinder) {
        this.metadataCollector = metadataCollector;
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
                metadataBuildingContext.getMetadataCollector(),
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
        if (metadataCollector.isInSecondPass() || isIdentifierBound(refDomainClass)) {
            alignWithReferencedIdentifier(property, value, compositeId, refDomainClass);
        } else {
            // The referenced identifier is bound later in this pass. Hibernate sorts a to-one against
            // the identifier it references lazily, from its second passes, so do the same.
            metadataCollector.addSecondPass(
                    new CompositeForeignKeySecondPass(this, property, value, compositeId, refDomainClass));
        }
    }

    /**
     * Aligns the columns of {@code value} with the composite identifier of {@code refDomainClass}:
     * sorts them the way Hibernate sorts that identifier, creates the foreign key against the matching
     * identifier columns and marks the value sorted. A to-one part of the referenced identifier that
     * still awaits its own alignment is aligned first, because its columns are the columns this key
     * references. Does nothing for a to-one that is sorted already.
     *
     * @param property the property the key belongs to
     * @param value the foreign-key value bound for {@code property}
     * @param compositeId the composite identity of the referenced entity
     * @param refDomainClass the referenced entity
     */
    public void alignWithReferencedIdentifier(
            HibernatePersistentProperty property,
            SimpleValue value,
            HibernateCompositeIdentity compositeId,
            GrailsHibernatePersistentEntity refDomainClass) {
        if (value instanceof ToOne toOne && toOne.isSorted()) {
            return;
        }
        for (IdentifierPart part : compositeIdentifierParts(refDomainClass)) {
            alignWithReferencedIdentifier(part.property(), part.value(), part.compositeId(), part.entity());
        }
        refDomainClass.sortOrIndexForeignKeyColumns(value);
        List<Column> referencedColumns = refDomainClass.getReferencedIdentifierColumns(compositeId.getPropertyNames());
        if (referencedColumns.isEmpty()) {
            // no identifier columns to pair with, so Hibernate's own foreign key pairs the columns by position
            value.createForeignKey();
        } else if (value.createForeignKeyOfEntity(refDomainClass.getName(), referencedColumns) != null) {
            value.disableForeignKey();
        }
        property.markValueSorted(value);
    }

    /**
     * Whether the identifier of {@code entity} is bound, down to the identifiers that its to-one
     * parts reference, so that a key referencing it can be aligned with it now.
     */
    private boolean isIdentifierBound(GrailsHibernatePersistentEntity entity) {
        return entity.getPersistentClass() != null &&
                compositeIdentifierParts(entity).stream()
                        .allMatch(part -> part.value().isSorted() || isIdentifierBound(part.entity()));
    }

    /**
     * The to-one parts of the identifier of {@code entity} that reference a composite identifier
     * themselves, each with the Hibernate value it is bound to. Empty while {@code entity} is unbound.
     */
    private static List<IdentifierPart> compositeIdentifierParts(GrailsHibernatePersistentEntity entity) {
        PersistentClass pc = entity.getPersistentClass();
        if (pc == null || !(pc.getIdentifier() instanceof Component component)) {
            return List.of();
        }
        return component.getProperties().stream()
                .filter(part -> part.getValue() instanceof ToOne)
                .flatMap(part -> identifierPart(entity, part.getName(), (ToOne) part.getValue()).stream())
                .toList();
    }

    private static Optional<IdentifierPart> identifierPart(
            GrailsHibernatePersistentEntity entity, String name, ToOne value) {
        if (!(entity.getHibernatePropertyByName(name) instanceof HibernateToOneProperty property)) {
            return Optional.empty();
        }
        GrailsHibernatePersistentEntity associated = property.getHibernateAssociatedEntity();
        return associated.getHibernateCompositeIdentity()
                .map(compositeId -> new IdentifierPart(property, value, compositeId, associated));
    }

    private record IdentifierPart(
            HibernateToOneProperty property,
            ToOne value,
            HibernateCompositeIdentity compositeId,
            GrailsHibernatePersistentEntity entity) {}

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
        // Hibernate sorts the properties of a composite identifier by name, and the columns this key
        // references follow that order, so the foreign key columns are named in the same order
        return Optional.of(Arrays.stream(nestedComposite)
                .sorted(Comparator.comparing(HibernatePersistentProperty::getName))
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
