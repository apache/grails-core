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
package org.grails.datastore.gorm

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import org.springframework.transaction.PlatformTransactionManager

import grails.gorm.MultiTenant
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.core.connections.ConnectionSourcesSupport
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.reflect.MetaClassUtils

/**
 * Enhances a class with GORM methods
 *
 * @author Graeme Rocher
 */
@CompileStatic
@SuppressWarnings(['unused', 'GroovyUnusedDeclaration', 'GroovyAssignabilityCheck', 'MethodMayBeStatic', 'AccessToStaticFieldLockedOnInstance'])
class GormEnhancer implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(GormEnhancer)
    private static final GormEnhancerRegistry STATE_REGISTRY = GormEnhancerRegistry.getInstance()

    private GormRegistry registry
    private List<String> connectionSourceNames
    Datastore datastore
    boolean failOnError = false
    boolean markDirty = true

    /**
     * Whether to include external entities
     */
    @SuppressWarnings(['unused', 'GrUnusedDeclaration'])
    boolean includeExternal = true

    /**
     * Backward-compatible constructor for callers that pass failOnError as a boolean.
     */
    GormEnhancer(Datastore datastore, PlatformTransactionManager transactionManager, boolean failOnError) {
        this(datastore, transactionManager, new ConnectionSourceSettings().failOnError(failOnError))
    }

    /**
     * Construct a new GormEnhancer for the given arguments.
     *
     * @param datastore The datastore (required)
     * @param transactionManager Retained for constructor compatibility
     * @param settings The connection source settings (required)
     */
    GormEnhancer(Datastore datastore,
                 @SuppressWarnings('unused') PlatformTransactionManager ignoredTransactionManager,
                 ConnectionSourceSettings settings) {
        this(datastore, ignoredTransactionManager, settings, GormRegistry.getInstance())
    }

    /**
     * Construct a new GormEnhancer for the given arguments.
     *
     * @param datastore The datastore (required)
     * @param transactionManager Retained for constructor compatibility
     * @param settings The connection source settings (required)
     * @param registry The GORM registry (required)
     */
    GormEnhancer(Datastore datastore,
                 @SuppressWarnings('unused') PlatformTransactionManager ignoredTransactionManager,
                 ConnectionSourceSettings settings,
                 GormRegistry registry) {
        assert datastore != null, 'Datastore is required'
        assert settings != null, 'ConnectionSourceSettings is required'
        
        this.datastore = datastore
        this.registry = registry
        
        this.failOnError = settings.isFailOnError()
        Boolean markDirty = settings.getMarkDirty()
        this.markDirty = markDirty == null ? true : markDirty

        this.connectionSourceNames = ConnectionSourceNameResolver.resolveConnectionSourceNames(datastore)

        String qualifier = ConnectionSourceNameResolver.resolveDefaultConnectionSourceName(datastore)
        registry.initializeDatastore(datastore, qualifier)

        for (entity in datastore.mappingContext.persistentEntities) {
            registerEntity(entity)
        }
    }

    /**
     * Registers a new entity with the GORM enhancer
     *
     * @param entity The entity
     */
    void registerEntity(PersistentEntity entity) {
        if (!entity.isExternal()) {
            // Delegate entity registration orchestration to the registry
            registry.registerEntity(entity, this)
            
            // Add dynamic methods to the class
            addStaticMethods(entity)
            addInstanceMethods(entity)
        }
    }

    /**
     * Obtain all of the qualifiers (typically the connection names) for the datastore and entity
     *
     * @param datastore The datastore
     * @param entity The entity
     * @return The qualifiers
     */
    @CompileStatic
    List<String> allQualifiers(Datastore datastore, PersistentEntity entity) {
        List<String> qualifiers = new ArrayList<>()
        qualifiers.addAll(ConnectionSourcesSupport.getConnectionSourceNames(entity))

        boolean isMultiTenant = MultiTenant.isAssignableFrom(entity.javaClass)
        boolean hasExplicitAll = qualifiers.contains(ConnectionSource.ALL)
        boolean hasExplicitNonDefaultDatasource = isMultiTenant &&
                !hasExplicitAll &&
                qualifiers.size() > 0 &&
                !qualifiers.equals(ConnectionSourcesSupport.DEFAULT_CONNECTION_SOURCE_NAMES)

        if ((isMultiTenant || hasExplicitAll) && !hasExplicitNonDefaultDatasource) {
            qualifiers.clear()
            if (datastore == this.datastore) {
                qualifiers.addAll(connectionSourceNames)
            } else {
                String className = entity.name
                for (String q in connectionSourceNames) {
                    if (registry.getDatastore(className, q) == datastore) {
                        qualifiers.add(q)
                    }
                }

                if (qualifiers.isEmpty()) {
                    for (String q in registry.datastoresByQualifier.keySet()) {
                        if (registry.datastoresByQualifier.get(q) == datastore) {
                            qualifiers.add(q)
                        }
                    }
                }
            }
        }

        if (qualifiers.isEmpty()) {
            qualifiers.add(ConnectionSource.DEFAULT)
        }
        return qualifiers.unique()
    }

    @SuppressWarnings(['unused', 'GrUnusedDeclaration'])
    List<String> getConnectionSourceNames() {
        return connectionSourceNames
    }

    /**
     * @return The GORM registry instance
     */
    static GormRegistry getRegistry() {
        return GormRegistry.instance
    }

    static PersistentEntity findEntity(Class entity) {
        GormApiResolver resolver = GormRegistry.instance.apiResolver
        Datastore ds = resolver.findDatastore(entity)
        return ds?.mappingContext?.getPersistentEntity(entity.name)
    }

    /**
     * Closes the enhancer clearing any stored static state
     */
    @CompileStatic
    void close() throws IOException {
        removeConstraints()
        if (STATE_REGISTRY.getPreferredDatastore() == datastore) {
            STATE_REGISTRY.clearPreferredDatastore()
        }
        registry.removeDatastore(datastore)
        def metaClassRegistry = GroovySystem.metaClassRegistry
        for (entity in datastore.mappingContext.persistentEntities) {
            def cls = entity.javaClass
            def className = cls.name
            registry.removeEntityDatastore(className, datastore)
            
            boolean stillManaged = (registry.getStaticApi(className) != null)
            
            if (!stillManaged) {
                metaClassRegistry.removeMetaClass(cls)
            }
        }
    }

    @CompileDynamic
    protected void removeConstraints() {
        try {
            def cls = Class.forName('org.grails.datastore.gorm.validation.constraints.eval.ConstraintsEvaluator', false, GormEnhancer.classLoader)
            if (cls != null) {
                def factory = datastore.mappingContext.mappingFactory
                if (factory.hasProperty('entityContext')) {
                    def constraintsEvaluator = factory.entityContext.getBean(cls)
                    if (constraintsEvaluator != null) {
                        for (entity in datastore.mappingContext.persistentEntities) {
                            constraintsEvaluator.removeConstraints(entity.javaClass)
                        }
                    }
                }
            }
        } catch (Throwable e) {
            log.debug("Not running in Grails environment, cannot de-register constraints. ${e.message}")
        }
    }

    @CompileDynamic
    protected void addStaticMethods(PersistentEntity e) {
        def cls = e.javaClass
        ExpandoMetaClass mc = MetaClassUtils.getExpandoMetaClass(cls)
        
        mc.static.setProperty('methodMissing', { String name, args ->
            def api = registry.resolveStaticApi(cls)
            try {
                return api.invokeMethod(name, args)
            } catch (MissingMethodException mme) {
                if (mme.method == name && mme.type == api.class) {
                    return api.methodMissing(name, args)
                }
                throw mme
            }
        })
        mc.static.setProperty('propertyMissing', { String name ->
            def api = registry.resolveStaticApi(cls)
            try {
                return api.getProperty(name)
            } catch (MissingPropertyException mpe) {
                if (mpe.property == name && mpe.type == api.class) {
                    return api.propertyMissing(name)
                }
                throw mpe
            }
        })
    }

    @CompileDynamic
    protected void addInstanceMethods(PersistentEntity e) {
        Class cls = e.javaClass
        ExpandoMetaClass mc = MetaClassUtils.getExpandoMetaClass(cls)
        
        mc.setProperty('methodMissing', { String name, args ->
            def api = registry.resolveInstanceApi(cls)
            try {
                return api.invokeMethod(name, args)
            } catch (MissingMethodException mme) {
                if (mme.method == name && mme.type == api.class) {
                    return api.methodMissing(delegate, name, args)
                }
                throw mme
            }
        })
        mc.setProperty('propertyMissing', { String name ->
            def api = registry.resolveInstanceApi(cls)
            try {
                return api.getProperty(name)
            } catch (MissingPropertyException mpe) {
                if (mpe.property == name && mpe.type == api.class) {
                    return api.propertyMissing(delegate, name)
                }
                throw mpe
            }
        })
        mc.setProperty('propertyMissing', { String name, val ->
            registry.resolveInstanceApi(cls).setProperty(name, val)
        })
    }

}
