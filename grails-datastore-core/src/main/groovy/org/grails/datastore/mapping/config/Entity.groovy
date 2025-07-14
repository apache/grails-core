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
package org.grails.datastore.mapping.config

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.builder.Builder
import groovy.transform.builder.SimpleStrategy
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.model.config.GormProperties

/**
 * Base class for classes returned from {@link org.grails.datastore.mapping.model.ClassMapping#getMappedForm()}
 *
 * @author Graeme Rocher
 * @since 1.1.9
 */
@CompileStatic
@Builder(builderStrategy = SimpleStrategy, prefix = '')
class Entity<P extends Property> {

    /**
     * @deprecated Use {@link ConnectionSource#DEFAULT} instead
     */
    @Deprecated
    public static final String ALL_DATA_SOURCES = ConnectionSource.ALL

    /**
     * @deprecated Use {@link ConnectionSource#ALL} instead
     */
    @Deprecated
    public static final String DEFAULT_DATA_SOURCE = ConnectionSource.DEFAULT

    /**
     * The configuration for each property
     */
    protected Map<String, P> propertyConfigs = [:]

    /**
     * @return Whether the entity state should be held in the session or not
     */
    boolean stateless = false
    /**
     * @return Whether automatic time stamps should be applied to 'lastUpdate' and 'dateCreated' properties
     */
    boolean autoTimestamp = true

    /**
     * @return Whether the entity should be autowired
     */
    boolean autowire = false

    /**
     * @return The default sort order definition, could be a string or a map
     */
    Object defaultSort = null

    /**
     * @return Whether the entity is versioned
     */
    boolean version = true

    /**
     * @return The property configurations
     */
    Map<String, P> getPropertyConfigs() {
        return propertyConfigs
    }

    Object getSort() {
        return defaultSort
    }

    Entity<P> setSort(Object defaultSort) {
        this.defaultSort = defaultSort
        return this
    }

    /**
     * Get the datasource names that this domain class works with.
     * @return the datasource names
     */
    List<String> datasources = [ConnectionSource.DEFAULT]

    /**
     * Sets the datastore to use
     *
     * @param name
     * @return
     */
    Entity<P> datasource(String name) {
        this.datasources = [name]
        return this
    }

    /**
     * Sets the datastore to use
     *
     * @param name
     * @return
     */
    Entity<P> connection(String name) {
        this.datasources = [name]
        return this
    }

    /**
     * Sets the connection to use
     *
     * @param name
     * @return
     */
    Entity<P> connections(String... names) {
        connections(Arrays.asList(names))
        return this
    }

    /**
     * Sets the connection to use
     *
     * @param name
     * @return
     */
    Entity<P> connections(List<String> names) {
        if (names != null && names.size() > 0) {
            this.datasources = names
        }
        return this
    }

    /**
     * @return Whether this entity is versioned
     */
    boolean isVersioned() {
        return version
    }

    /**
     * Get a property config
     * @param name The name of the property
     * @return
     */
    P getPropertyConfig(String name) { propertyConfigs[name] }

    /**
     * Define the identity config
     * @param identityConfig The id config
     * @return This mapping
     */
    Entity<P> id(Map identityConfig) {
        Property.configureExisting(
                getOrInitializePropertyConfig(GormProperties.IDENTITY),
                identityConfig
        )
        return this
    }
    /**
     * Define the identity config
     * @param identityConfig The id config
     * @return This mapping
     */
    Entity<P> id(@DelegatesTo(type = 'P') Closure identityConfig) {
        Property.configureExisting(
                getOrInitializePropertyConfig(GormProperties.IDENTITY),
                identityConfig
        )
        return this
    }
    /**
     * <p>Configures the name of the version column
     * <code> { version 'foo' }
     *
     * @param isVersioned True if a version property should be configured
     */
    Entity<P> version(@DelegatesTo(type = 'P') Closure versionConfig) {
        P pc = getOrInitializePropertyConfig(GormProperties.VERSION)
        Property.configureExisting(pc, versionConfig)
        return this
    }
    /**
     * <p>Configures the name of the version column
     * <code> { version 'foo' }
     *
     * @param isVersioned True if a version property should be configured
     */
    Entity<P> version(Map versionConfig) {
        P pc = getOrInitializePropertyConfig(GormProperties.VERSION)
        Property.configureExisting(pc, versionConfig)
        return this
    }

    /**
     * Sets the tenant id
     *
     * @param tenantIdProperty The tenant id property
     */
    Entity<P> tenantId(String tenantIdProperty) {
        P pc = getOrInitializePropertyConfig(GormProperties.TENANT_IDENTITY)
        pc.name = tenantIdProperty
        return this
    }
    /**
     * Configure a property
     * @param name The name of the property
     * @param propertyConfig The property config
     * @return This mapping
     */
    Entity<P> property(String name, @DelegatesTo(type = 'P') Closure propertyConfig) {
        P pc = getOrInitializePropertyConfig(name)
        Property.configureExisting(pc, propertyConfig)
        return this
    }

    /**
     * Configure a property
     * @param name The name of the property
     * @param propertyConfig The property config
     * @return This mapping
     */
    Entity<P> property(String name, Map propertyConfig) {
        P pc = getOrInitializePropertyConfig(name)
        Property.configureExisting(pc, propertyConfig)
        return this
    }

    /**
     * Configure a new property
     * @param name The name of the property
     * @param propertyConfig The property config
     * @return This mapping
     */
    P property(@DelegatesTo(type = 'P') Closure propertyConfig) {
        if (propertyConfigs.containsKey('*')) {
            P cloned = cloneGlobalConstraint()
            return Property.configureExisting(cloned, propertyConfig)
        } else {
            P prop = newProperty()
            return Property.configureExisting(prop, propertyConfig)
        }
    }

    /**
     * Configure a new property
     * @param name The name of the property
     * @param propertyConfig The property config
     * @return This mapping
     */
    P property(Map propertyConfig) {
        if (propertyConfigs.containsKey('*')) {
            // apply global constraints constraints
            P cloned = cloneGlobalConstraint()
            return Property.configureExisting(cloned, propertyConfig)
        } else {
            P prop = newProperty()
            return Property.configureExisting(prop, propertyConfig)
        }
    }

    /**
     * Configures an existing Mapping instance
     *
     * @param config The configuration
     * @return The new instance
     */
    static <T extends Entity> T configureExisting(@DelegatesTo.Target T mapping, @DelegatesTo(genericTypeIndex = 0) Closure config) {
        config.setDelegate(mapping)
        config.setResolveStrategy(Closure.DELEGATE_ONLY)
        config.call()
        return mapping
    }

    def propertyMissing(String name, Object val) {
        if (val instanceof Closure) {
            property(name, (Closure) val)
        } else if (val instanceof Property) {
            propertyConfigs[name] = ((P) val)
        } else {
            throw new MissingPropertyException(name, Entity)
        }
    }

    @CompileDynamic
    def methodMissing(String name, Object args) {
        if (args && args.getClass().isArray()) {
            if (args[0] instanceof Closure) {
                property(name, (Closure) args[0])
            } else if (args[0] instanceof Property) {
                propertyConfigs[name] = (P) args[0]
            } else if (args[0] instanceof Map) {
                P property = getOrInitializePropertyConfig(name)
                Map namedArgs = (Map) args[0]
                if (args[-1] instanceof Closure) {
                    Property.configureExisting(
                            property,
                            ((Closure) args[-1])
                    )

                }
                Property.configureExisting(property, namedArgs)
            } else {
                throw new MissingMethodException(name, getClass(), args)
            }
        } else {
            throw new MissingMethodException(name, getClass(), args)
        }
    }

    protected P getOrInitializePropertyConfig(String name) {
        P pc = propertyConfigs[name]
        if (pc == null && propertyConfigs.containsKey('*')) {
            // apply global constraints constraints
            P globalConstraints = propertyConfigs.get('*')
            if (globalConstraints != null) {
                pc = (P) globalConstraints.clone()
            }
        } else {
            pc = propertyConfigs[name]
        }
        if (pc == null) {
            pc = newProperty()
            propertyConfigs[name] = pc
        }
        return pc
    }

    protected P newProperty() {
        (P) new Property()
    }

    protected P cloneGlobalConstraint() {
        // apply global constraints constraints
        P globalConstraints = propertyConfigs.get('*')
        P cloned = (P) globalConstraints.clone()
        return cloned
    }
}
