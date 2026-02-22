/*
 * Copyright 2003-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.orm.hibernate.cfg.domainbinding.hibernate

import groovy.transform.CompileStatic
import org.grails.datastore.mapping.config.groovy.MappingConfigurationBuilder
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher
import org.hibernate.FetchMode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import jakarta.persistence.AccessType
import org.grails.orm.hibernate.cfg.CacheConfig
import org.grails.orm.hibernate.cfg.SortConfig
import org.grails.orm.hibernate.cfg.ColumnConfig
import org.grails.orm.hibernate.cfg.CompositeIdentity
import org.grails.orm.hibernate.cfg.Identity
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.NaturalId
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.grails.orm.hibernate.cfg.PropertyDefinitionDelegate

/**
 * Implements the ORM mapping DSL constructing a model that can be evaluated by the
 * GrailsDomainBinder class which maps GORM classes onto the database.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class HibernateMappingBuilder implements MappingConfigurationBuilder<Mapping, PropertyConfig> {

    private static final String INCLUDE_PARAM = 'include'
    private static final String EXCLUDE_PARAM = 'exclude'
    static final Logger LOG = LoggerFactory.getLogger(this)

    Mapping mapping
    final String className
    final Closure defaultConstraints

    private List<String> methodMissingExcludes = []
    private List<String> methodMissingIncludes

    HibernateMappingBuilder(String className) {
        this.className = className
    }

    HibernateMappingBuilder(Mapping mapping, String className, Closure defaultConstraints = null) {
        this.mapping = mapping
        this.className = className
        this.defaultConstraints = defaultConstraints
    }

    @Override
    Map<String, PropertyConfig> getProperties() {
        return mapping.columns
    }

    @Override
    Mapping evaluate(@DelegatesTo(value = HibernateMappingBuilder, strategy = Closure.DELEGATE_ONLY) Closure mappingClosure, Object context = null) {
        if (mapping == null) {
            mapping = new Mapping()
        }
        mappingClosure.resolveStrategy = Closure.DELEGATE_ONLY
        mappingClosure.delegate = this
        try {
            if (context != null) {
                mappingClosure.call(context)
            } else {
                mappingClosure.call()
            }
        } finally {
            mappingClosure.delegate = null
        }
        mapping
    }

    void includes(@DelegatesTo(value = HibernateMappingBuilder, strategy = Closure.DELEGATE_ONLY) Closure callable) {
        if (!callable) {
            return
        }
        callable.resolveStrategy = Closure.DELEGATE_ONLY
        callable.delegate = this
        try {
            callable.call()
        } finally {
            callable.delegate = null
        }
    }

    void hibernateCustomUserType(Map args) {
        if (args.type && (args['class'] instanceof Class)) {
            mapping.userTypes[(Class)args['class']] = args.type.toString()
        }
    }

    void table(String name) {
        mapping.tableName = name
    }

    void discriminator(String name) {
        mapping.discriminator(name)
    }

    void discriminator(Map args) {
        mapping.discriminator(args)
    }

    void autoImport(boolean b) {
        mapping.autoImport = b
    }

    void table(Map tableDef) {
        mapping.table.name = tableDef?.name?.toString()
        mapping.table.schema = tableDef?.schema?.toString()
        mapping.table.catalog = tableDef?.catalog?.toString()
    }

    void sort(String name) {
        if (name) {
            SortConfig sc = (SortConfig) mapping.getSort()
            sc.name = name
        }
    }

    void autowire(boolean autowire) {
        mapping.autowire = autowire
    }

    void dynamicUpdate(boolean b) {
        mapping.dynamicUpdate = b
    }

    void dynamicInsert(boolean b) {
        mapping.dynamicInsert = b
    }

    void sort(Map namesAndDirections) {
        if (namesAndDirections) {
            SortConfig sc = (SortConfig) mapping.getSort()
            sc.namesAndDirections = (Map<String, String>)namesAndDirections
        }
    }

    void batchSize(Integer num) {
        if (num) {
            mapping.batchSize = num
        }
    }

    void order(String direction) {
        if ("desc".equalsIgnoreCase(direction) || "asc".equalsIgnoreCase(direction)) {
            SortConfig sc = (SortConfig) mapping.getSort()
            sc.direction = direction
        }
    }

    void autoTimestamp(boolean b) {
        mapping.autoTimestamp = b
    }

    void version(boolean isVersioned) {
        mapping.version(isVersioned)
    }

    void version(String versionColumn) {
        mapping.version(versionColumn)
    }

    void tenantId(String tenantIdProperty) {
        mapping.tenantId(tenantIdProperty)
    }

    void cache(Map args) {
        mapping.cache = new CacheConfig(enabled: true)
        if (args.usage) {
            String usage = args.usage.toString()
            if (CacheConfig.USAGE_OPTIONS.contains(usage)) {
                mapping.cache.usage = usage
            } else {
                LOG.warn("ORM Mapping Invalid: Specified [usage] with value [$usage] of [cache] in class [$className] is not valid")
            }
        }
        if (args.include) {
            String include = args.include.toString()
            if (CacheConfig.INCLUDE_OPTIONS.contains(include)) {
                mapping.cache.include = include
            } else {
                LOG.warn("ORM Mapping Invalid: Specified [include] with value [$include] of [cache] in class [$className] is not valid")
            }
        }
    }

    void cache(String usage) {
        cache(usage: usage)
    }

    void cache(String usage, Map args) {
        Map finalArgs = args ? new HashMap(args) : [:]
        finalArgs.usage = usage
        cache(finalArgs)
    }

    void tablePerHierarchy(boolean isTablePerHierarchy) {
        mapping.tablePerHierarchy = isTablePerHierarchy
    }

    void tablePerSubclass(boolean isTablePerSubClass) {
        mapping.tablePerHierarchy = !isTablePerSubClass
    }

    void tablePerConcreteClass(boolean isTablePerConcreteClass) {
        if (isTablePerConcreteClass) {
            mapping.tablePerHierarchy = false
            mapping.tablePerConcreteClass = true
        }
    }

    void cache(boolean shouldCache) {
        mapping.cache = new CacheConfig(enabled: shouldCache)
    }

    void id(Map args) {
        if (args.composite) {
            mapping.identity = new CompositeIdentity(propertyNames: (String[]) args.composite)
            if (args.compositeClass) {
                (mapping.identity as CompositeIdentity).compositeClass = (Class) args.compositeClass
            }
        } else {
            if (args?.generator) {
                ((Identity) mapping.identity).generator = args.remove('generator').toString()
            }
            if (args?.name) {
                ((Identity) mapping.identity).name = args.remove('name').toString()
            }
            if (args?.params) {
                Map params = (Map) args.remove('params')
                Map<String, String> stringParams = [:]
                params.each { k, v -> stringParams[k.toString()] = v?.toString() }
                ((Identity) mapping.identity).params = stringParams
            }
        }
        if (args?.natural) {
            Object naturalArgs = args.remove('natural')
            Object propertyNames = naturalArgs instanceof Map ? ((Map) naturalArgs).remove('properties') : naturalArgs
            if (propertyNames) {
                NaturalId ni = new NaturalId()
                ni.mutable = (naturalArgs instanceof Map) && ((Map) naturalArgs).mutable ?: false
                if (propertyNames instanceof List) {
                    ni.propertyNames = (List<String>) propertyNames
                } else {
                    ni.propertyNames = [propertyNames.toString()]
                }
                mapping.identity.natural = ni
            }
        }
        if (!args.composite && args) {
            handlePropertyInternal("id", args, null)
        }
    }

    /**
     * Typed property method for CompileStatic support.
     */
    void property(Map args, String name) {
        handlePropertyInternal(name, args, null)
    }

    /**
     * Internal logic for building property configurations.
     */
    protected void handlePropertyInternal(String name, Map namedArgs, Closure subClosure) {
        PropertyConfig newConfig = new PropertyConfig()
        if (defaultConstraints != null && namedArgs.containsKey('shared')) {
            PropertyConfig sharedConstraints = mapping.columns.get(namedArgs.shared.toString())
            if (sharedConstraints != null) {
                newConfig = (PropertyConfig) sharedConstraints.clone()
            }
        } else if (mapping.columns.containsKey('*')) {
            PropertyConfig globalConstraints = mapping.columns.get('*')
            if (globalConstraints != null) {
                newConfig = (PropertyConfig) globalConstraints.clone()
            }
        }

        PropertyConfig property = mapping.columns[name] ?: newConfig
        property.name = namedArgs.name?.toString() ?: property.name
        property.generator = namedArgs.generator?.toString() ?: property.generator
        property.formula = namedArgs.formula?.toString() ?: property.formula
        property.accessType = namedArgs.accessType instanceof AccessType ? (AccessType)namedArgs.accessType : property.accessType
        property.type = namedArgs.type ?: property.type
        property.setLazy(namedArgs.lazy instanceof Boolean ? (Boolean)namedArgs.lazy : property.getLazy())
        property.insertable = namedArgs.insertable instanceof Boolean ? (Boolean)namedArgs.insertable : property.insertable
        property.updatable = (namedArgs.updateable != null ? namedArgs.updateable : namedArgs.updatable) instanceof Boolean ? (Boolean)(namedArgs.updateable ?: namedArgs.updatable) : property.updatable
        property.cascade = namedArgs.cascade?.toString() ?: property.cascade
        property.cascadeValidate = namedArgs.cascadeValidate instanceof Boolean ? (Boolean)namedArgs.cascadeValidate : property.cascadeValidate
        property.sort = namedArgs.sort?.toString() ?: property.sort
        property.order = namedArgs.order?.toString() ?: property.order
        property.batchSize = namedArgs.batchSize instanceof Integer ? (Integer)namedArgs.batchSize : property.batchSize
        property.ignoreNotFound = namedArgs.ignoreNotFound instanceof Boolean ? (Boolean)namedArgs.ignoreNotFound : property.ignoreNotFound
        if (namedArgs.params instanceof Map) {
            Properties typeProps = new Properties()
            ((Map<Object, Object>)namedArgs.params).each { k, v -> typeProps.put(k, v) }
            property.typeParams = typeProps
        }

        if (namedArgs.unique instanceof Boolean) property.setUnique((boolean)(Boolean)namedArgs.unique)
        else if (namedArgs.unique instanceof String) property.setUnique((String)namedArgs.unique)
        else if (namedArgs.unique instanceof List) property.setUnique((List<String>)namedArgs.unique)
        property.nullable = namedArgs.nullable instanceof Boolean ? (Boolean)namedArgs.nullable : property.nullable
        property.maxSize = namedArgs.maxSize instanceof Number ? (Number)namedArgs.maxSize : property.maxSize
        property.minSize = namedArgs.minSize instanceof Number ? (Number)namedArgs.minSize : property.minSize

        if (namedArgs.size instanceof IntRange) property.size = (IntRange) namedArgs.size
        property.max = namedArgs.max instanceof Comparable ? (Comparable) namedArgs.max : property.max
        property.min = namedArgs.min instanceof Comparable ? (Comparable) namedArgs.min : property.min
        property.range = namedArgs.range instanceof ObjectRange ? (ObjectRange) namedArgs.range : null
        property.inList = namedArgs.inList instanceof List ? (List) namedArgs.inList : property.inList

        if (namedArgs.scale instanceof Integer) property.scale = (Integer) namedArgs.scale

        if (namedArgs.fetch) {
            String fetchStr = namedArgs.fetch.toString()
            if (fetchStr.equalsIgnoreCase("join")) property.fetch = FetchMode.JOIN
            else if (fetchStr.equalsIgnoreCase("select")) property.fetch = FetchMode.SELECT
            else property.fetch = FetchMode.DEFAULT
        }

        if (subClosure != null) {
            subClosure.delegate = new PropertyDefinitionDelegate(property)
            subClosure.resolveStrategy = Closure.DELEGATE_ONLY
            subClosure.call()
        } else {
            ColumnConfig cc = property.columns ? property.columns[0] : new ColumnConfig()
            if (!property.columns) property.columns << cc

            if (namedArgs["column"]) cc.name = namedArgs["column"].toString()
            if (namedArgs["sqlType"]) cc.sqlType = namedArgs["sqlType"].toString()
            if (namedArgs["enumType"]) cc.enumType = namedArgs["enumType"].toString()
            if (namedArgs["index"]) cc.index = namedArgs["index"].toString()
            if (namedArgs["unique"]) cc.unique = namedArgs["unique"]
            if (namedArgs["read"]) cc.read = namedArgs["read"].toString()
            if (namedArgs["write"]) cc.write = namedArgs["write"].toString()
            if (namedArgs.defaultValue) cc.defaultValue = namedArgs.defaultValue.toString()
            if (namedArgs.comment) cc.comment = namedArgs.comment.toString()
            if (namedArgs["length"] instanceof Integer) cc.length = (Integer)namedArgs["length"]
            if (namedArgs["precision"] instanceof Integer) cc.precision = (Integer)namedArgs["precision"]
            if (namedArgs["scale"] instanceof Integer) cc.scale = (Integer)namedArgs["scale"]

            if (namedArgs.joinTable instanceof String) {
                property.joinTable((String)namedArgs.joinTable)
            } else if (namedArgs.joinTable instanceof Map) {
                property.joinTable((Map)namedArgs.joinTable)
            }

            if (namedArgs.indexColumn instanceof Map) {
                Map icArgs = (Map)namedArgs.indexColumn
                PropertyConfig ic = new PropertyConfig()
                ColumnConfig icc = new ColumnConfig()
                if (icArgs.name) icc.name = icArgs.name.toString()
                if (icArgs.type) icc.sqlType = icArgs.type.toString()
                if (icArgs.length instanceof Integer) icc.length = (Integer)icArgs.length
                ic.columns << icc
                ic.type = icArgs.type
                property.indexColumn = ic
            }
        }

        // Cache association handling
        if (namedArgs.cache != null) {
            CacheConfig cc = new CacheConfig()
            if (namedArgs.cache instanceof String && CacheConfig.USAGE_OPTIONS.contains(namedArgs.cache)) {
                cc.usage = namedArgs.cache.toString()
                property.cache = cc
            } else if (namedArgs.cache == true) {
                property.cache = cc
            } else if (namedArgs.cache instanceof Map) {
                Map cacheArgs = (Map) namedArgs.cache
                cc.usage = cacheArgs.usage?.toString()
                cc.include = cacheArgs.include?.toString()
                property.cache = cc
            }
        }

        mapping.columns[name] = property
    }

    void columns(@DelegatesTo(value = Object, strategy = Closure.DELEGATE_ONLY) Closure callable) {
        callable.resolveStrategy = Closure.DELEGATE_ONLY
        callable.delegate = new Object() {
            def invokeMethod(String methodName, Object args) {
                Object[] argsArray = (Object[]) args
                Map namedArgs = (argsArray.length > 0 && argsArray[0] instanceof Map) ? (Map)argsArray[0] : [:]
                Closure sub = (argsArray.length > 0 && argsArray[argsArray.length - 1] instanceof Closure) ? (Closure)argsArray[argsArray.length - 1] : null
                handlePropertyInternal(methodName, namedArgs, sub)
            }
        }
        callable.call()
    }

    void datasource(String name) {
        mapping.datasources = [name]
    }

    void datasources(List<String> names) {
        mapping.datasources = names
    }

    void comment(String comment) {
        mapping.comment = comment
    }

    def methodMissing(String name, Object args) {
        if (methodMissingIncludes != null && !methodMissingIncludes.contains(name)) return
        if (methodMissingExcludes.contains(name)) return

        Object[] argsArray = (Object[]) args
        boolean hasArgs = argsArray.length > 0
        if (name == 'user-type' && hasArgs && argsArray[0] instanceof Map) {
            hibernateCustomUserType((Map) argsArray[0])
        } else if (name == 'importFrom' && hasArgs && argsArray[0] instanceof Class) {
            List<Closure> constraintsToImport = ClassPropertyFetcher.getStaticPropertyValuesFromInheritanceHierarchy(
                (Class) argsArray[0], GormProperties.CONSTRAINTS, Closure)
            if (constraintsToImport) {
                List<String> originalIncludes = this.methodMissingIncludes
                List<String> originalExcludes = this.methodMissingExcludes
                try {
                    Object lastArg = argsArray[argsArray.length - 1]
                    if (lastArg instanceof Map) {
                        Map argMap = (Map) lastArg
                        Object includes = argMap.get(INCLUDE_PARAM)
                        Object excludes = argMap.get(EXCLUDE_PARAM)
                        if (includes instanceof List) this.methodMissingIncludes = (List<String>) includes
                        if (excludes instanceof List) this.methodMissingExcludes = (List<String>) excludes
                    }
                    for (Closure callable in constraintsToImport) {
                        callable.delegate = this
                        callable.resolveStrategy = Closure.DELEGATE_ONLY
                        callable.call()
                    }
                } finally {
                    this.methodMissingIncludes = originalIncludes
                    this.methodMissingExcludes = originalExcludes
                }
            }
        } else if (hasArgs && (argsArray[0] instanceof Map || argsArray[0] instanceof Closure)) {
            Map namedArgs = argsArray[0] instanceof Map ? (Map)argsArray[0] : [:]
            Closure sub = argsArray[argsArray.length - 1] instanceof Closure ? (Closure)argsArray[argsArray.length - 1] : null
            handlePropertyInternal(name, namedArgs, sub)
        }
    }
}