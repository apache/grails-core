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

    void hibernateCustomUserType(Map<String, Object> args) {
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
        if ('desc'.equalsIgnoreCase(direction) || 'asc'.equalsIgnoreCase(direction)) {
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
                mapping.cache.usage = CacheConfig.Usage.of(usage)
            } else {
                LOG.warn("ORM Mapping Invalid: Specified [usage] with value [$usage] of [cache] in class [$className] is not valid")
            }
        }
        if (args.include) {
            String include = args.include.toString()
            if (CacheConfig.INCLUDE_OPTIONS.contains(include)) {
                mapping.cache.include = CacheConfig.Include.of(include)
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

    void id(Map<String, Object> args) {
        if (args.composite) {
            mapping.identity = new CompositeIdentity(propertyNames: (String[]) args.composite)
            if (args.compositeClass) {
                (mapping.identity as CompositeIdentity).compositeClass = (Class) args.compositeClass
            }
        } else {
            Object generatorVal = args.remove('generator')
            if (generatorVal != null) {
                ((Identity) mapping.identity).generator = generatorVal.toString()
            }
            Object nameVal = args.remove('name')
            if (nameVal != null) {
                ((Identity) mapping.identity).name = nameVal.toString()
            }
            Object paramsVal = args.remove('params')
            if (paramsVal instanceof Map) {
                Map<String, String> stringParams = [:]
                ((Map<Object, Object>) paramsVal).each { k, v -> stringParams[k.toString()] = v?.toString() }
                ((Identity) mapping.identity).params = stringParams
            }
        }
        Object naturalVal = args.remove('natural')
        if (naturalVal != null) {
            Object propertyNames = naturalVal instanceof Map ? ((Map<String, Object>) naturalVal).remove('properties') : naturalVal
            if (propertyNames) {
                NaturalId ni = new NaturalId()
                ni.mutable = (naturalVal instanceof Map) && ((Map<String, Object>) naturalVal).mutable ?: false
                if (propertyNames instanceof List) {
                    ni.propertyNames = (List<String>) propertyNames
                } else {
                    ni.propertyNames = [propertyNames.toString()]
                }
                mapping.identity.natural = ni
            }
        }
        if (!args.composite && args) {
            handlePropertyInternal('id', args, null)
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
    protected void handlePropertyInternal(String name, Map<String, Object> namedArgs, Closure subClosure) {
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
        Object nameVal = namedArgs.name
        if (nameVal != null) property.name = nameVal.toString()
        Object genVal = namedArgs.generator
        if (genVal != null) property.generator = genVal.toString()
        Object formulaVal = namedArgs.formula
        if (formulaVal != null) property.formula = formulaVal.toString()
        if (namedArgs.accessType instanceof AccessType) property.accessType = (AccessType) namedArgs.accessType
        Object typeVal = namedArgs.type
        if (typeVal != null) property.type = typeVal
        if (namedArgs.lazy instanceof Boolean) property.setLazy((Boolean) namedArgs.lazy)
        if (namedArgs.insertable instanceof Boolean) property.insertable = (Boolean) namedArgs.insertable
        if (namedArgs.updatable instanceof Boolean) property.updatable = (Boolean) namedArgs.updatable
        if (namedArgs.updateable instanceof Boolean) {
            LOG.warn("'updateable' is deprecated in domain class mapping; use 'updatable' instead")
            property.updatable = (Boolean) namedArgs.updateable
        }
        Object cascadeVal = namedArgs.cascade
        if (cascadeVal != null) property.cascade = cascadeVal.toString()
        if (namedArgs.cascadeValidate instanceof Boolean) property.cascadeValidate = (Boolean) namedArgs.cascadeValidate
        Object sortVal = namedArgs.sort
        if (sortVal != null) property.sort = sortVal.toString()
        Object orderVal = namedArgs.order
        if (orderVal != null) property.order = orderVal.toString()
        if (namedArgs.batchSize instanceof Integer) property.batchSize = (Integer) namedArgs.batchSize
        if (namedArgs.batchSize instanceof Integer) property.batchSize = (Integer) namedArgs.batchSize
        if (namedArgs.ignoreNotFound instanceof Boolean) property.ignoreNotFound = (Boolean) namedArgs.ignoreNotFound
        if (namedArgs.params instanceof Map) {
            Properties typeProps = new Properties()
            ((Map<Object, Object>) namedArgs.params).each { Object k, Object v -> typeProps.put(k, v) }
            property.typeParams = typeProps
        }

        Object uniqueVal = namedArgs.unique
        if (uniqueVal instanceof Boolean) property.setUnique((boolean)(Boolean) uniqueVal)
        else if (uniqueVal instanceof String) property.setUnique((String) uniqueVal)
        else if (uniqueVal instanceof List) property.setUnique((List<String>) uniqueVal)
        if (namedArgs.nullable instanceof Boolean) property.nullable = (Boolean) namedArgs.nullable
        if (namedArgs.maxSize instanceof Number) property.maxSize = (Number) namedArgs.maxSize
        if (namedArgs.minSize instanceof Number) property.minSize = (Number) namedArgs.minSize
        if (namedArgs.size instanceof IntRange) property.size = (IntRange) namedArgs.size
        if (namedArgs.max instanceof Comparable) property.max = (Comparable) namedArgs.max
        if (namedArgs.min instanceof Comparable) property.min = (Comparable) namedArgs.min
        if (namedArgs.range instanceof ObjectRange) property.range = (ObjectRange) namedArgs.range
        if (namedArgs.inList instanceof List) property.inList = (List) namedArgs.inList
        if (namedArgs.scale instanceof Integer) property.scale = (Integer) namedArgs.scale

        if (namedArgs.fetch) {
            String fetchStr = namedArgs.fetch.toString()
            if (fetchStr.equalsIgnoreCase('join')) property.fetch = FetchMode.JOIN
            else if (fetchStr.equalsIgnoreCase('select')) property.fetch = FetchMode.SELECT
            else property.fetch = FetchMode.DEFAULT
        }

        if (subClosure != null) {
            subClosure.delegate = new PropertyDefinitionDelegate(property)
            subClosure.resolveStrategy = Closure.DELEGATE_ONLY
            subClosure.call()
        } else {
            ColumnConfig cc = property.columns ? property.columns[0] : new ColumnConfig()
            if (!property.columns) property.columns << cc

            Object colVal = namedArgs['column']
            if (colVal) cc.name = colVal.toString()
            Object sqlTypeVal = namedArgs['sqlType']
            if (sqlTypeVal) cc.sqlType = sqlTypeVal.toString()
            Object enumTypeVal = namedArgs['enumType']
            if (enumTypeVal) cc.enumType = enumTypeVal.toString()
            Object indexVal = namedArgs['index']
            if (indexVal) cc.index = indexVal.toString()
            Object ccUniqueVal = namedArgs['unique']
            if (ccUniqueVal) cc.unique = ccUniqueVal instanceof Boolean ? (Boolean) ccUniqueVal : ccUniqueVal
            Object readVal = namedArgs['read']
            if (readVal) cc.read = readVal.toString()
            Object writeVal = namedArgs['write']
            if (writeVal) cc.write = writeVal.toString()
            Object defaultVal = namedArgs.defaultValue
            if (defaultVal) cc.defaultValue = defaultVal.toString()
            Object commentVal = namedArgs.comment
            if (commentVal) cc.comment = commentVal.toString()
            if (namedArgs['length'] instanceof Integer) cc.length = (int) (Integer) namedArgs['length']
            if (namedArgs['precision'] instanceof Integer) cc.precision = (int) (Integer) namedArgs['precision']
            if (namedArgs['scale'] instanceof Integer) cc.scale = (int) (Integer) namedArgs['scale']

            Object joinTableVal = namedArgs.joinTable
            if (joinTableVal instanceof String) {
                property.joinTable((String) joinTableVal)
            } else if (joinTableVal instanceof Map) {
                property.joinTable((Map) joinTableVal)
            }

            if (namedArgs.indexColumn instanceof Map) {
                Map<String, Object> icArgs = (Map<String, Object>) namedArgs.indexColumn
                PropertyConfig ic = new PropertyConfig()
                ColumnConfig icc = new ColumnConfig()
                Object icName = icArgs.name
                if (icName) icc.name = icName.toString()
                Object icType = icArgs.type
                if (icType) icc.sqlType = icType.toString()
                if (icArgs.length instanceof Integer) icc.length = (int) (Integer) icArgs.length
                ic.columns << icc
                ic.type = icType
                property.indexColumn = ic
            }
        }

        // Cache association handling
        if (namedArgs.cache != null) {
            CacheConfig cc = new CacheConfig()
            Object cacheVal = namedArgs.cache
            if (cacheVal instanceof String && CacheConfig.USAGE_OPTIONS.contains(cacheVal)) {
                cc.usage = CacheConfig.Usage.of(cacheVal)
                property.cache = cc
            } else if (cacheVal == true) {
                property.cache = cc
            } else if (cacheVal instanceof Map) {
                Map<String, Object> cacheArgs = (Map<String, Object>) cacheVal
                Object cacheUsage = cacheArgs.usage
                if (cacheUsage != null) cc.usage = CacheConfig.Usage.of(cacheUsage)
                Object cacheInclude = cacheArgs.include
                if (cacheInclude != null) cc.include = CacheConfig.Include.of(cacheInclude)
                property.cache = cc
            }
        }

        mapping.columns[name] = property
    }

    void columns(@DelegatesTo(value = Object, strategy = Closure.DELEGATE_ONLY) Closure callable) {
        callable.resolveStrategy = Closure.DELEGATE_ONLY
        callable.delegate = new Object() {
            Object invokeMethod(String methodName, Object args) {
                Object[] argsArray = (Object[]) args
                int argc = argsArray.length
                Map<String, Object> namedArgs = (argc > 0 && argsArray[0] instanceof Map) ? (Map<String, Object>) argsArray[0] : [:]
                Closure sub = (argc > 0 && argsArray[argc - 1] instanceof Closure) ? (Closure) argsArray[argc - 1] : null
                handlePropertyInternal(methodName, namedArgs, sub)
                return null
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

    void methodMissing(String name, Object args) {
        if (methodMissingIncludes != null && !methodMissingIncludes.contains(name)) return
        if (methodMissingExcludes.contains(name)) return

        Object[] argsArray = (Object[]) args
        int argc = argsArray.length
        boolean hasArgs = argc > 0
        Object firstArg = hasArgs ? argsArray[0] : null
        Object lastArg = argc > 0 ? argsArray[argc - 1] : null

        HibernateMappingKeyword keyword = HibernateMappingKeyword.fromString(name)
        if (keyword == HibernateMappingKeyword.USER_TYPE && hasArgs && firstArg instanceof Map) {
            hibernateCustomUserType((Map<String, Object>) firstArg)
        } else if (keyword == HibernateMappingKeyword.IMPORT_FROM && hasArgs && firstArg instanceof Class) {
            List<Closure> constraintsToImport = ClassPropertyFetcher.getStaticPropertyValuesFromInheritanceHierarchy(
                (Class) firstArg, GormProperties.CONSTRAINTS, Closure)
            if (constraintsToImport) {
                List<String> originalIncludes = methodMissingIncludes
                List<String> originalExcludes = methodMissingExcludes
                try {
                    if (lastArg instanceof Map) {
                        Map<String, Object> argMap = (Map<String, Object>) lastArg
                        Object includes = argMap.get(INCLUDE_PARAM)
                        Object excludes = argMap.get(EXCLUDE_PARAM)
                        if (includes instanceof List) methodMissingIncludes = (List<String>) includes
                        if (excludes instanceof List) methodMissingExcludes = (List<String>) excludes
                    }
                    for (Closure callable in constraintsToImport) {
                        callable.delegate = this
                        callable.resolveStrategy = Closure.DELEGATE_ONLY
                        callable.call()
                    }
                } finally {
                    methodMissingIncludes = originalIncludes
                    methodMissingExcludes = originalExcludes
                }
            }
        } else if (hasArgs && (firstArg instanceof Map || firstArg instanceof Closure)) {
            Map<String, Object> namedArgs = firstArg instanceof Map ? (Map<String, Object>) firstArg : [:]
            Closure sub = lastArg instanceof Closure ? (Closure) lastArg : null
            handlePropertyInternal(name, namedArgs, sub)
        }
    }
}
