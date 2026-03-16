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
package org.grails.orm.hibernate.cfg.domainbinding.secondpass;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jakarta.annotation.Nonnull;

import org.hibernate.MappingException;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.DependantValue;
import org.hibernate.mapping.ManyToOne;
import org.hibernate.mapping.OneToMany;
import org.hibernate.mapping.PersistentClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.grails.datastore.mapping.model.DatastoreConfigurationException;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.orm.hibernate.cfg.domainbinding.binder.CollectionForPropertyConfigBinder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.ManyToOneBinder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueColumnBinder;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher;
import org.grails.orm.hibernate.cfg.domainbinding.util.OrderByClauseBuilder;

import static org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder.EMPTY_PATH;

/** Refactored from CollectionBinder to handle collection second pass binding. */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class CollectionSecondPassBinder {

    private static final Logger LOG = LoggerFactory.getLogger(CollectionSecondPassBinder.class);

    private final DefaultColumnNameFetcher defaultColumnNameFetcher;
    private final OrderByClauseBuilder orderByClauseBuilder;
    private final ManyToOneBinder manyToOneBinder;
    private final PrimaryKeyValueCreator primaryKeyValueCreator;
    private final CollectionKeyColumnUpdater collectionKeyColumnUpdater;
    private final BidirectionalOneToManyLinker bidirectionalOneToManyLinker;
    private final DependentKeyValueBinder dependentKeyValueBinder;
    private final UnidirectionalOneToManyBinder unidirectionalOneToManyBinder;
    private final CollectionWithJoinTableBinder collectionWithJoinTableBinder;
    private final CollectionForPropertyConfigBinder collectionForPropertyConfigBinder;
    private final SimpleValueColumnBinder simpleValueColumnBinder;

    /** Creates a new {@link CollectionSecondPassBinder} instance. */
    public CollectionSecondPassBinder(
            ManyToOneBinder manyToOneBinder,
            PrimaryKeyValueCreator primaryKeyValueCreator,
            CollectionKeyColumnUpdater collectionKeyColumnUpdater,
            BidirectionalOneToManyLinker bidirectionalOneToManyLinker,
            DependentKeyValueBinder dependentKeyValueBinder,
            UnidirectionalOneToManyBinder unidirectionalOneToManyBinder,
            CollectionWithJoinTableBinder collectionWithJoinTableBinder,
            CollectionForPropertyConfigBinder collectionForPropertyConfigBinder,
            DefaultColumnNameFetcher defaultColumnNameFetcher,
            SimpleValueColumnBinder simpleValueColumnBinder) {
        this.manyToOneBinder = manyToOneBinder;
        this.primaryKeyValueCreator = primaryKeyValueCreator;
        this.collectionKeyColumnUpdater = collectionKeyColumnUpdater;
        this.bidirectionalOneToManyLinker = bidirectionalOneToManyLinker;
        this.dependentKeyValueBinder = dependentKeyValueBinder;
        this.unidirectionalOneToManyBinder = unidirectionalOneToManyBinder;
        this.collectionWithJoinTableBinder = collectionWithJoinTableBinder;
        this.collectionForPropertyConfigBinder = collectionForPropertyConfigBinder;
        this.defaultColumnNameFetcher = defaultColumnNameFetcher;
        this.simpleValueColumnBinder = simpleValueColumnBinder;
        this.orderByClauseBuilder = new OrderByClauseBuilder();
    }

    /** Bind collection second pass. */
    public void bindCollectionSecondPass(
            @Nonnull HibernateToManyProperty property,
            @Nonnull InFlightMetadataCollector mappings,
            Map<?, ?> persistentClasses,
            @Nonnull Collection collection) {

        PersistentClass associatedClass = bindOrderBy(property, collection, persistentClasses);

        if (collection.isOneToMany()) {
            OneToMany oneToMany = (OneToMany) collection.getElement();
            oneToMany.setAssociatedClass(associatedClass);
            if (property.shouldBindWithForeignKey()) {
                collection.setCollectionTable(associatedClass.getTable());
            }
            collectionForPropertyConfigBinder.bindCollectionForPropertyConfig(collection, property);
        }

        Optional.ofNullable(property.getHibernateAssociatedEntity())
                .filter(referenced -> !(property instanceof HibernateManyToManyProperty) && referenced.isMultiTenant())
                .map(referenced -> referenced.getMultiTenantFilterCondition(defaultColumnNameFetcher))
                .ifPresent(filterCondition -> {
                    if (property.isUnidirectionalOneToMany()) {
                        collection.addManyToManyFilter(
                                GormProperties.TENANT_IDENTITY,
                                filterCondition,
                                true,
                                Collections.emptyMap(),
                                Collections.emptyMap());
                    } else {
                        collection.addFilter(
                                GormProperties.TENANT_IDENTITY,
                                filterCondition,
                                true,
                                Collections.emptyMap(),
                                Collections.emptyMap());
                    }
                });

        if (property.isSorted()) {
            collection.setSorted(true);
        }

        DependantValue key = primaryKeyValueCreator.createPrimaryKeyValue(collection);
        collection.setKey(key);

        if (property.isBidirectional()) {
            if (property.getHibernateInverseSide() instanceof org.grails.datastore.mapping.model.types.ToOne &&
                    property.shouldBindWithForeignKey()) {
                bidirectionalOneToManyLinker.link(collection, associatedClass, key, property.getHibernateInverseSide());
            } else if (property.getHibernateInverseSide() instanceof HibernateManyToManyProperty ||
                    java.util.Map.class.isAssignableFrom(property.getType())) {
                dependentKeyValueBinder.bind(property, key);
            }
        } else {
            if (property.getMappedForm().hasJoinKeyMapping()) {
                simpleValueColumnBinder.bindSimpleValue(
                        key,
                        "long",
                        property.getMappedForm().getJoinTable().getKey().getName(),
                        true);
            } else {
                dependentKeyValueBinder.bind(property, key);
            }
        }

        Optional.ofNullable(property.getMappedForm().getCache())
                .ifPresent(cacheConfig -> collection.setCacheConcurrencyStrategy(cacheConfig.getUsage()));

        if (property instanceof HibernateManyToManyProperty || property.isBidirectionalOneToManyMap()) {
            if (property.isBidirectional()) {
                var otherSide = property.getHibernateInverseSide();
                ManyToOne element =
                        manyToOneBinder.bindManyToOne(otherSide, collection.getCollectionTable(), EMPTY_PATH);
                element.setReferencedEntityName(otherSide.getOwner().getName());
                collection.setElement(element);
                collectionForPropertyConfigBinder.bindCollectionForPropertyConfig(collection, property);
                if (property.isCircular()) {
                    collection.setInverse(false);
                }
            }
        } else if (property.isUnidirectionalOneToMany()) {
            unidirectionalOneToManyBinder.bind((HibernateOneToManyProperty) property, mappings, collection);
        } else if (property.supportsJoinColumnMapping()) {
            collectionWithJoinTableBinder.bindCollectionWithJoinTable(property, mappings, collection);
        }
        collectionKeyColumnUpdater.forceNullableAndCheckUpdatable(key, property);
    }

    PersistentClass bindOrderBy(HibernateToManyProperty property, Collection collection, Map<?, ?> persistentClasses) {
        return Optional.ofNullable(property.getHibernateAssociatedEntity())
                .map(referenced -> {
                    if (referenced.isTablePerHierarchySubclass()) {
                        String discriminatorColumnName = referenced.getDiscriminatorColumnName();
                        Set<String> discSet = referenced.buildDiscriminatorSet();
                        String inclause = String.join(",", discSet);

                        collection.setWhere(discriminatorColumnName + " in (" + inclause + ")");
                    }

                    PersistentClass associatedClass = (PersistentClass) persistentClasses.get(referenced.getName());
                    if (associatedClass == null) {
                        throw new MappingException("Association references unmapped class: " + referenced.getName());
                    }

                    if (property.hasSort()) {
                        if (!property.isBidirectional() && property instanceof HibernateOneToManyProperty) {
                            throw new DatastoreConfigurationException("Default sort for associations [" +
                                    property.getHibernateOwner().getName() +
                                    "->" +
                                    property.getName() +
                                    "] are not supported with unidirectional one to many relationships.");
                        }
                        HibernatePersistentProperty sortBy =
                                (HibernatePersistentProperty) referenced.getPropertyByName(property.getSort());
                        String order = Optional.ofNullable(property.getOrder()).orElse("asc");
                        collection.setOrderBy(orderByClauseBuilder.buildOrderByClause(
                                sortBy.getName(), associatedClass, collection.getRole(), order));
                    }
                    return associatedClass;
                })
                .orElse(null);
    }
}
