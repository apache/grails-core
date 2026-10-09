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
package org.grails.orm.hibernate.cfg.domainbinding.jpa

import org.hibernate.FetchMode
import org.hibernate.engine.spi.FilterDefinition
import org.hibernate.generator.Generator
import org.hibernate.id.enhanced.SequenceStyleGenerator
import org.hibernate.id.enhanced.TableGenerator
import org.hibernate.mapping.Backref
import org.hibernate.mapping.Bag
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Collection as HibernateCollection
import org.hibernate.mapping.Column
import org.hibernate.mapping.Component
import org.hibernate.mapping.DependantValue
import org.hibernate.mapping.FilterConfiguration
import org.hibernate.mapping.Formula
import org.hibernate.mapping.Index
import org.hibernate.mapping.IndexBackref
import org.hibernate.mapping.IndexedCollection
import org.hibernate.mapping.JoinedSubclass
import org.hibernate.mapping.List as HibernateList
import org.hibernate.mapping.ManyToOne
import org.hibernate.mapping.Map as HibernateMap
import org.hibernate.mapping.OneToMany
import org.hibernate.mapping.OneToOne
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.Property
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Selectable
import org.hibernate.mapping.Set as HibernateSet
import org.hibernate.mapping.Table
import org.hibernate.mapping.ToOne
import org.hibernate.mapping.UniqueKey
import org.hibernate.mapping.Value

import org.grails.orm.hibernate.cfg.ColumnConfig
import org.grails.orm.hibernate.cfg.IdentityEnumType
import org.grails.orm.hibernate.cfg.JoinTable

/**
 * The facts of a bound Hibernate mapping model that the generator differential reads, as plain maps and lists.
 *
 * <p>The same extraction describes the objects the classic domain binder bound (recorded by {@link ClassicOracle}, see
 * {@code GrailsDomainGeneratorDifferentialSpec}) and the objects Hibernate's annotation binder built from the generated classes
 * (read live), so that one comparison reads both. A fact keeps the name the Hibernate getter has ({@code nullable},
 * {@code cascade}, ...); a fact that is null is left out of the map. Only what a comparison reads is recorded.</p>
 */
class ClassicFacts {

    private static final Map<String, Class<?>> VALUE_TYPES = [
            BasicValue    : BasicValue,
            Collection    : HibernateCollection,
            Component     : Component,
            DependantValue: DependantValue,
            IndexedCollection: IndexedCollection,
            ManyToOne     : ManyToOne,
            OneToMany     : OneToMany,
            OneToOne      : OneToOne,
            ToOne         : ToOne,
    ]

    /** The {@code lazy} flag of a {@code Property} nothing has set, for a value compared as a property of its own. */
    static final boolean DEFAULT_PROPERTY_LAZY = new Property().lazy

    static boolean isA(Map value, String type) {
        return value != null && ((List<String>) value.types).contains(type)
    }

    /** The columns of a property's value or of a value (formulas are not columns). */
    static List<Map> columnsOf(Map holder) {
        Map value = holder.types != null ? holder : (Map) holder.value
        return value.selectables == null ? [] : ((List<Map>) value.selectables).findAll { Map selectable -> selectable.formula == null }
    }

    static Map column(Column column) {
        return [
                name      : column.name,
                text      : column.text,
                quoted    : column.quoted,
                nullable  : column.nullable,
                unique    : column.unique,
                length    : column.length?.intValue(),
                precision : column.precision?.intValue(),
                scale     : column.scale?.intValue(),
                sqlType   : column.sqlType,
                defaultValue: column.defaultValue,
                customRead: column.customRead,
                customWrite: column.customWrite,
                comment   : column.comment,
        ]
    }

    static Map selectable(Selectable selectable) {
        if (selectable instanceof Formula) {
            return [formula: ((Formula) selectable).getFormula(), text: selectable.text]
        }
        return column((Column) selectable)
    }

    static String fetchMode(FetchMode mode) {
        return mode == FetchMode.JOIN ? 'JOIN' : 'SELECT'
    }

    static String collectionKind(HibernateCollection collection) {
        if (collection instanceof HibernateList) {
            return CollectionKind.LIST.name()
        }
        if (collection instanceof HibernateMap) {
            return CollectionKind.MAP.name()
        }
        if (collection instanceof HibernateSet) {
            return (collection.sorted ? CollectionKind.SORTED_SET : CollectionKind.SET).name()
        }
        return collection instanceof Bag ? CollectionKind.BAG.name() : null
    }

    static Map value(Value value) {
        if (value == null) {
            return null
        }
        Map facts = [
                kind : value.getClass().simpleName,
                types: VALUE_TYPES.findAll { String name, Class<?> type -> type.isInstance(value) }.keySet().toList(),
        ]
        if (!(value instanceof HibernateCollection) && !(value instanceof OneToMany)) {
            facts.selectables = value.selectables.collect { Selectable each -> selectable(each) }
        }
        if (value instanceof BasicValue) {
            basicValue((BasicValue) value, facts)
        }
        if (value instanceof ToOne) {
            ToOne toOne = (ToOne) value
            facts.referencedEntityName = toOne.referencedEntityName
            facts.referencedPropertyName = toOne.referencedPropertyName
            facts.lazy = toOne.lazy
            facts.fetchMode = fetchMode(toOne.fetchMode)
            facts.ignoreNotFound = value instanceof ManyToOne && ((ManyToOne) value).ignoreNotFound
            if (value instanceof OneToOne) {
                facts.constrained = ((OneToOne) value).constrained
                facts.foreignKeyType = ((OneToOne) value).foreignKeyType?.name()
            }
        }
        if (value instanceof OneToMany) {
            facts.referencedEntityName = ((OneToMany) value).referencedEntityName
        }
        if (value instanceof DependantValue) {
            facts.nullable = ((DependantValue) value).nullable
            facts.updateable = ((DependantValue) value).updateable
        }
        if (value instanceof Component) {
            Component component = (Component) value
            facts.members = component.properties.collect { Property each -> property(each) }
            facts.embedded = component.embedded
            facts.nullValue = component.nullValue
        }
        if (value instanceof HibernateCollection) {
            collection((HibernateCollection) value, facts)
        }
        return facts
    }

    private static void basicValue(BasicValue value, Map facts) {
        facts.typeName = value.typeName
        facts.enumerationStyle = value.enumerationStyle?.name()
        facts.enumStyle = value.typeName == IdentityEnumType.name ? 'IDENTITY' : value.enumerationStyle?.name()
        Map<String, String> parameters = [:]
        value.typeParameters?.stringPropertyNames()?.each { String key -> parameters[key] = value.typeParameters.getProperty(key) }
        facts.typeParameters = parameters
        try {
            BasicValue.Resolution<?> resolution = value.resolve()
            facts.jdbcCode = resolution.jdbcType.defaultSqlTypeCode
            facts.javaType = resolution.domainJavaType.javaTypeClass.name
            facts.converter = resolution.valueConverter?.getClass()?.name
        } catch (Exception ignored) {
            // a value that cannot be resolved has no resolved facts, which a comparison reads as a difference
        }
    }

    private static void collection(HibernateCollection collection, Map facts) {
        facts.collectionKind = collectionKind(collection)
        facts.lazy = collection.lazy
        facts.extraLazy = collection.extraLazy
        facts.fetchMode = fetchMode(collection.fetchMode)
        facts.batchSize = collection.batchSize
        facts.cacheConcurrencyStrategy = collection.cacheConcurrencyStrategy
        facts.inverse = collection.inverse
        facts.orphanDelete = collection.hasOrphanDelete()
        facts.oneToMany = collection.oneToMany
        facts.orderBy = collection.orderBy
        facts.manyToManyOrdering = collection.manyToManyOrdering
        facts.where = collection.where
        facts.role = collection.role
        facts.collectionTable = collection.collectionTable == null ? null : table(collection.collectionTable)
        facts.key = value(collection.key)
        facts.element = value(collection.element)
        if (collection instanceof IndexedCollection) {
            facts.index = value(((IndexedCollection) collection).index)
        }
        facts.filters = collection.filters*.condition
        facts.manyToManyFilters = collection.manyToManyFilters*.condition
    }

    static Map property(Property property) {
        return [
                name             : property.name,
                insertable       : property.insertable,
                updateable       : property.updateable,
                optional         : property.optional,
                lazy             : property.lazy,
                cascade          : property.cascade,
                naturalIdentifier: property.naturalIdentifier,
                backref          : property instanceof Backref || property instanceof IndexBackref,
                value            : value(property.value),
        ]
    }

    /** A value compared like a property of its own (the key, element or index of a collection). */
    static Map propertyOf(Map value) {
        return [value: value, lazy: DEFAULT_PROPERTY_LAZY]
    }

    static Map table(Table table) {
        return [
                name              : table.name,
                schema            : table.schema,
                catalog           : table.catalog,
                comment           : table.comment,
                abstractTable     : table.isAbstract(),
                abstractUnionTable: table.isAbstractUnionTable(),
                columns           : table.columns*.name,
                primaryKey        : table.primaryKey?.columns*.name,
                indexes           : table.indexes.values().collect { Index index -> [name: index.name, columns: index.columns*.name] }.sort { it.name },
                uniqueKeys        : table.uniqueKeys.values().collect { UniqueKey key -> [name: key.name, columns: key.columns*.name] }.sort { it.name },
                foreignKeys       : table.foreignKeys.values().collect { key -> key.columns*.name },
        ]
    }

    /**
     * One class of the mapping model: its table (with a token that tells whether two classes share a table), the properties by name
     * (the declared ones, those the class inherits, the identifier property and any of {@code names} it has), the composite identifier,
     * the cache of a root, the discriminator of a root and the tenant filters the class added itself.
     */
    static Map persistentClass(PersistentClass persistentClass, Collection<String> names, Map<Table, String> tokens) {
        Table mapped = persistentClass.table
        if (!tokens.containsKey(mapped)) {
            tokens[mapped] = "t${tokens.size() + 1}".toString()
        }
        Map<String, Map> properties = new TreeMap<String, Map>()
        Set<String> wanted = new TreeSet<String>(names)
        wanted.addAll(persistentClass.propertyClosure*.name)
        if (persistentClass.identifierProperty != null) {
            properties[persistentClass.identifierProperty.name] = property(persistentClass.identifierProperty)
        }
        // the properties a comparison walks (the declared ones and the class's own) are recorded as they are, the others by the name asked for
        for (Property declared : persistentClass.declaredProperties + persistentClass.properties) {
            if (!properties.containsKey(declared.name)) {
                properties[declared.name] = property(declared)
            }
        }
        for (String name : wanted) {
            if (!properties.containsKey(name) && persistentClass.hasProperty(name)) {
                properties[name] = property(persistentClass.getProperty(name))
            }
        }
        Map facts = [
                kind             : persistentClass.getClass().simpleName,
                entityName       : persistentClass.entityName,
                jpaEntityName    : persistentClass.jpaEntityName,
                superclass       : persistentClass.superclass?.entityName,
                table            : table(mapped) + [token: tokens[mapped]],
                ownsTable        : persistentClass.superclass == null || !mapped.is(persistentClass.superclass.table),
                dynamicInsert    : persistentClass.useDynamicInsert(),
                dynamicUpdate    : persistentClass.useDynamicUpdate(),
                batchSize        : persistentClass.batchSize,
                optimisticLock   : persistentClass.optimisticLockStyle?.name(),
                abstractClass    : Boolean.TRUE == persistentClass.isAbstract(),
                discriminatorValue: persistentClass.discriminatorValue,
                declaredNames    : persistentClass.declaredProperties*.name,
                propertyNames    : persistentClass.properties*.name,
                props            : properties,
                identifierKind   : persistentClass.identifier?.getClass()?.simpleName,
                identifierProperty: persistentClass.identifierProperty?.name,
                identifierMapper : persistentClass.identifierMapper?.properties*.name,
                ownFilters       : persistentClass.filters.findAll { FilterConfiguration filter ->
                    filter.name == 'tenantId' && (persistentClass.superclass == null || !persistentClass.superclass.filters.any { it.is(filter) })
                }.collect { FilterConfiguration filter -> [condition: filter.condition, autoAlias: filter.useAutoAliasInjection()] },
        ]
        if (persistentClass.identifier instanceof Component) {
            facts.identifier = value(persistentClass.identifier)
        }
        if (persistentClass instanceof JoinedSubclass) {
            facts.keyColumns = ((JoinedSubclass) persistentClass).key.columns*.name
        }
        if (persistentClass instanceof RootClass) {
            RootClass root = (RootClass) persistentClass
            facts.cacheConcurrencyStrategy = root.cacheConcurrencyStrategy
            facts.cached = root.cached
            facts.mutable = root.mutable
            facts.lazyPropertiesCacheable = root.lazyPropertiesCacheable
            facts.discriminator = value(root.discriminator)
            facts.discriminatorInsertable = root.isDiscriminatorInsertable()
        }
        return facts
    }

    static Map generator(Generator generator) {
        Map facts = [className: generator.getClass().name]
        if (generator instanceof SequenceStyleGenerator) {
            SequenceStyleGenerator sequence = (SequenceStyleGenerator) generator
            facts.sequence = [
                    name         : sequence.databaseStructure.physicalName.objectName.text,
                    incrementSize: sequence.optimizer.incrementSize,
                    optimizer    : sequence.optimizer.getClass().name,
            ]
        } else if (generator instanceof TableGenerator) {
            TableGenerator tableGenerator = (TableGenerator) generator
            facts.table = [tableName: tableGenerator.tableName, segmentValue: tableGenerator.segmentValue, incrementSize: tableGenerator.incrementSize]
        }
        return facts
    }

    static Map filterDefinition(FilterDefinition definition) {
        if (definition == null) {
            return null
        }
        Map<String, String> parameterTypes = [:]
        definition.parameterNames.each { String name ->
            parameterTypes[name] = definition.getParameterJdbcMapping(name)?.javaTypeDescriptor?.javaTypeClass?.name
        }
        return [
                name              : definition.filterName,
                parameterNames    : definition.parameterNames.toList().sort(),
                parameterTypes    : parameterTypes,
                defaultCondition  : definition.defaultFilterCondition,
                autoEnabled       : definition.autoEnabled,
                appliedToLoadByKey: definition.appliedToLoadByKey,
        ]
    }

    private static final List<String> COLUMN_CONFIG_FIELDS = [
            'name', 'sqlType', 'enumType', 'index', 'unique', 'length', 'precision', 'scale', 'defaultValue', 'comment', 'read', 'write'
    ]

    /**
     * The state of a join table mapping after the classic binder ran. The binder writes into the mapping while it binds (the inverse side of
     * a many-to-many adopts the owning side's join table, a circular many-to-many gets a renamed key), and the generator differential reads
     * the mapping the binder left, so the state is recorded to be put back on the mapping of a datastore the classic binder never bound.
     * A column config that holds anything JSON cannot (a closure as its index) fails here instead of being recorded unfaithfully.
     */
    static Map joinTableState(JoinTable joinTable) {
        if (joinTable == null) {
            return null
        }
        Map state = [
                name   : joinTable.name,
                schema : joinTable.schema,
                catalog: joinTable.catalog,
                keys   : joinTable.keys?.collect { ColumnConfig config -> columnConfigState(config) },
                column : columnConfigState(joinTable.column),
        ]
        return ClassicOracle.normalized(state) == [keys: []] ? null : state
    }

    private static Map columnConfigState(ColumnConfig config) {
        if (config == null) {
            return null
        }
        Map state = [:]
        for (String field : COLUMN_CONFIG_FIELDS) {
            Object value = config.getProperty(field)
            if (value != null && !(value instanceof CharSequence || value instanceof Number || value instanceof Boolean)) {
                throw new IllegalStateException("The column config ${config.name} has a ${field} of ${value.getClass().name}, which the classic oracle cannot record")
            }
            state[field] = value
        }
        return state
    }

    static JoinTable restoreJoinTable(Map state) {
        return new JoinTable(
                name: state.name, schema: state.schema, catalog: state.catalog,
                keys: ((List<Map>) (state.keys ?: [])).collect { Map key -> restoreColumnConfig(key) },
                column: state.column == null ? null : restoreColumnConfig((Map) state.column))
    }

    private static ColumnConfig restoreColumnConfig(Map state) {
        ColumnConfig config = new ColumnConfig()
        COLUMN_CONFIG_FIELDS.each { String field ->
            if (state.containsKey(field)) {
                config.setProperty(field, state[field])
            }
        }
        return config
    }
}
