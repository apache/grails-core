/*
 * Copyright 2003-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.orm.hibernate.cfg

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.builder.Builder
import groovy.transform.builder.SimpleStrategy
import org.grails.datastore.mapping.config.Entity
import org.grails.datastore.mapping.config.Property
import org.grails.datastore.mapping.model.config.GormProperties
import org.springframework.beans.MutablePropertyValues
import org.springframework.validation.DataBinder

/**
 * Models the mapping from GORM classes to the db.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
@Builder(builderStrategy = SimpleStrategy, prefix = '')
class Mapping extends Entity<PropertyConfig> {

    /**
     * Custom hibernate user types
     */
    Map userTypes = [:]

    /**
     * Return a type name of the known custom user types
     */
    String getTypeName(Class theClass) {
        def type = userTypes[theClass]
        if (type == null) {
            return null
        }

        return type instanceof Class ? ((Class)type).name : type.toString()
    }

    /**
     * The table
     */
    Table table = new Table()

    /**
     * The table name
     */
    String getTableName() { table?.name }

    /**
     * Set the table name
     */
    void setTableName(String name) { table?.name = name }

    /**
     * Whether the class is versioned for optimistic locking
     */
    boolean versioned = true

    /**
     * Sets whether to use table-per-hierarchy or table-per-subclass mapping
     */
    boolean tablePerHierarchy = true

    /**
     * Sets whether to use table-per-concrete-class or table-per-subclass mapping
     */
    boolean tablePerConcreteClass = false

    /**
     * Sets whether packaged domain classes should be auto-imported in HQL queries
     */
    boolean autoImport = true

    /**
     * The configuration for each property
     */
    Map<String, PropertyConfig> columns = [:]

    /**
     * The identity definition
     */
    Property identity = new Identity()

    /**
     * Caching config
     */
    CacheConfig cache

    /**
     * Used to hold the names and directions of the default property to sort by
     */
    SortConfig sort = new SortConfig()

    /**
     * Value used to discriminate entities in table-per-hierarchy inheritance mapping
     */
    DiscriminatorConfig discriminator

    /**
     * Obtains a PropertyConfig object for the given name
     */
    @Override
    PropertyConfig getPropertyConfig(String name) { columns[name] }

    /**
     * The batch size to use for lazy loading
     */
    Integer batchSize

    /**
     * Whether to use dynamically created update queries, at the cost of some performance
     */
    boolean dynamicUpdate = false

    /**
     * Whether to use dynamically created insert queries, at the cost of some performance
     */
    boolean dynamicInsert = false

    /**
     * DDL comment.
     */
    String comment


    boolean isTablePerConcreteClass() {
        return tablePerConcreteClass
    }

    void setTablePerConcreteClass(boolean tablePerConcreteClass) {
        this.tablePerHierarchy = !tablePerConcreteClass
        this.tablePerConcreteClass = tablePerConcreteClass
    }

    @Override
    Map<String, PropertyConfig> getPropertyConfigs() {
        return columns
    }
    /**
     * Define the table name
     * @param name The table name
     * @return This mapping
     */
    Mapping table(String name) {
        this.table.name = name
        return this
    }

    /**
     * Define the table config
     *
     * @param tableConfig The table config
     * @return This mapping
     */
    Mapping table(@DelegatesTo(Table) Closure tableConfig) {
        Table.configureExisting(table, tableConfig)
        return this
    }

    /**
     * Define the table config
     *
     * @param tableConfig The table config
     * @return This mapping
     */
    Mapping table(Map tableConfig) {
        Table.configureExisting(table, tableConfig)
        return this
    }
    /**
     * Define the identity config
     * @param identityConfig The id config
     * @return This mapping
     */
    @Override
    Mapping id(Map identityConfig) {
        if(identity instanceof Identity) {
            Identity.configureExisting((Identity)identity, identityConfig)
        }
        return this
    }
    /**
     * Define the identity config
     * @param identityConfig The id config
     * @return This mapping
     */
    @Override
    Mapping id(@DelegatesTo(Identity) Closure identityConfig) {
        if(identity instanceof Identity) {
            Identity.configureExisting((Identity)identity, identityConfig)
        }
        return this
    }

    /**
     * Define the identity config
     * @param identityConfig The id config
     * @return This mapping
     */

    Mapping id(CompositeIdentity compositeIdentity) {
        this.identity = compositeIdentity
        return this
    }

    /**
     * Define the cache config
     * @param cacheConfig The cache config
     * @return This mapping
     */
    Mapping cache(@DelegatesTo(CacheConfig) Closure cacheConfig) {
        if(this.cache == null) {
            this.cache = new CacheConfig()
        }
        CacheConfig.configureExisting(cache, cacheConfig)
        return this
    }

    /**
     * Define the cache config
     * @param cacheConfig The cache config
     * @return This mapping
     */
    Mapping cache(Map cacheConfig) {
        if(this.cache == null) {
            this.cache = new CacheConfig()
        }
        CacheConfig.configureExisting(cache, cacheConfig)
        return this
    }

    /**
     * Define the cache config
     * @param cacheConfig The cache config
     * @return This mapping
     */
    Mapping cache(String usage) {
        if(this.cache == null) {
            this.cache = new CacheConfig()
        }
        this.cache.usage = usage
        this.cache.enabled = true
        return this
    }


    /**
     * Configures sorting
     * @param name The name
     * @param direction The direction
     * @return This mapping
     */
    Mapping sort(String name, String direction) {
        if(name && direction) {
            this.sort.name = name
            this.sort.direction = direction
        }
        return this
    }

    /**
     * Configures sorting
     * @param name The name
     * @param direction The direction
     * @return This mapping
     */
    Mapping sort(Map nameAndDirections) {
        if(nameAndDirections) {
            this.sort.namesAndDirections = nameAndDirections
        }
        return this
    }

    /**
     * Configures the discriminator
     * @param discriminatorDef The discriminator
     * @return This mapping
     */
    Mapping discriminator(@DelegatesTo(DiscriminatorConfig) Closure discriminatorDef) {
        if(discriminator == null) {
            discriminator = new DiscriminatorConfig()
        }
        discriminatorDef.setDelegate(discriminator)
        discriminatorDef.setResolveStrategy(Closure.DELEGATE_ONLY)
        discriminatorDef.call()
        return this
    }

    /**
     * Configures the discriminator
     * @param the discriminator value
     * @return This mapping
     */
    Mapping discriminator(String value) {
        if(discriminator == null) {
            discriminator = new DiscriminatorConfig()
        }
        discriminator.value = value
        return this
    }

    /**
     * Configures the discriminator
     * @param discriminatorDef The discriminator
     * @return This mapping
     */
    Mapping discriminator(Map args) {
        if(args != null) {
            if(discriminator == null) {
                discriminator = new DiscriminatorConfig()
            }

            String value = args.remove('value')?.toString()
            discriminator.value = value
            if (args.column instanceof String) {
                discriminator.column = new ColumnConfig(name:args.column.toString())
            }
            else if (args.column instanceof Map) {
                ColumnConfig config = new ColumnConfig()
                DataBinder dataBinder = new DataBinder(config)
                dataBinder.bind(new MutablePropertyValues((Map)args.column))
                discriminator.column = config
            }
            discriminator.type(args.remove('type'))
            if(args.containsKey('insert')) {
                discriminator.insertable( args.remove('insert') as Boolean )
            }
            if(args.containsKey('insertable')) {
                discriminator.insertable( args.remove('insertable') as Boolean )
            }
            discriminator.formula(args.remove('formula')?.toString())
        }
        return this
    }

    /**
     * Define a new composite id
     * @param propertyNames
     * @return
     */
    CompositeIdentity composite(String...propertyNames) {
        identity = new CompositeIdentity(propertyNames: propertyNames)
        return (CompositeIdentity)identity
    }

    /**
     * <p>Configures whether to use versioning for optimistic locking
     * <code> { version false }
     *
     * @param isVersioned True if a version property should be configured
     */
    @CompileStatic
    Mapping version(boolean isVersioned) {
        versioned = isVersioned
        return this
    }

    /**
     * <p>Configures the name of the version column
     * <code> { version 'foo' }
     *
     * @param isVersioned True if a version property should be configured
     */
    @CompileStatic
    @Override
    Mapping version(Map versionConfig) {
        PropertyConfig pc = getOrInitializePropertyConfig(GormProperties.VERSION)
        PropertyConfig.configureExisting(pc, versionConfig)
        return this
    }

    /**
     * <p>Configures the name of the version column
     * <code> { version 'foo' }
     *
     * @param isVersioned True if a version property should be configured
     */
    @CompileStatic
    Mapping version(String versionColumn) {
        PropertyConfig pc = getOrInitializePropertyConfig(GormProperties.VERSION)
        pc.columns << new ColumnConfig(name:versionColumn)
        return this
    }


    /**
     * Configure a property
     * @param name The name of the property
     * @param propertyConfig The property config
     * @return This mapping
     */
    @Override
    Mapping property(String name, @DelegatesTo(PropertyConfig) Closure propertyConfig) {
        PropertyConfig pc = getOrInitializePropertyConfig(name)
        PropertyConfig.configureExisting(pc, propertyConfig)
        return this
    }

    /**
     * Configure a property
     * @param name The name of the property
     * @param propertyConfig The property config
     * @return This mapping
     */
    @Override
    Mapping property(String name, Map propertyConfig) {
        PropertyConfig pc = getOrInitializePropertyConfig(name)
        PropertyConfig.configureExisting(pc, propertyConfig)
        return this
    }

    /**
     * Configure a new property
     * @param name The name of the property
     * @param propertyConfig The property config
     * @return This mapping
     */
    @Override
    PropertyConfig property(@DelegatesTo(PropertyConfig) Closure propertyConfig) {
        if(columns.containsKey('*')) {
            PropertyConfig cloned = cloneGlobalConstraint()
            return PropertyConfig.configureExisting(cloned, propertyConfig)
        }
        else {
            return PropertyConfig.configureNew(propertyConfig)
        }
    }

    /**
     * Configure the version
     *
     * @param versionConfig The version config
     * @return This entity
     */
    @Override
    Entity version(@DelegatesTo(PropertyConfig) Closure versionConfig) {
        return super.version(versionConfig)
    }
    /**
     * Configure a new property
     * @param name The name of the property
     * @param propertyConfig The property config
     * @return This mapping
     */
    @Override
    PropertyConfig property( Map propertyConfig) {
        if(columns.containsKey('*')) {
            // apply global constraints constraints
            PropertyConfig cloned = cloneGlobalConstraint()
            return PropertyConfig.configureExisting(cloned, propertyConfig)
        }
        else {
            return PropertyConfig.configureNew(propertyConfig)
        }
    }

    /**
     * Configures a new Mapping instance
     *
     * @param config The configuration
     * @return The new instance
     */
    static Mapping configureNew(@DelegatesTo(Mapping) Closure config) {
        Mapping property = new Mapping()
        return configureExisting(property, config)
    }

    /**
     * Configures an existing Mapping instance
     *
     * @param config The configuration
     * @return The new instance
     */
    static Mapping configureExisting(Mapping mapping, Map config) {
        DataBinder dataBinder = new DataBinder(mapping)
        dataBinder.bind(new MutablePropertyValues(config))

        return mapping
    }

    /**
     * Configures an existing Mapping instance
     *
     * @param config The configuration
     * @return The new instance
     */
    static Mapping configureExisting(Mapping mapping, @DelegatesTo(Mapping) Closure config) {
        config.setDelegate(mapping)
        config.setResolveStrategy(Closure.DELEGATE_ONLY)
        config.call()
        return mapping
    }

    @Override
    def propertyMissing(String name, Object val) {
        if(val instanceof Closure) {
            property(name, (Closure)val)
        }
        else if(val instanceof PropertyConfig) {
            columns[name] =((PropertyConfig)val)
        }
        else {
            throw new MissingPropertyException(name, Mapping)
        }
    }

    @CompileDynamic
    @Override
    def methodMissing(String name, Object args) {
        if(args && args.getClass().isArray()) {
            if(args[0] instanceof Closure) {
                property(name, (Closure)args[0])
            }
            else if(args[0] instanceof PropertyConfig) {
                columns[name] = (PropertyConfig)args[0]
            }
            else if(args[0] instanceof Map) {
                PropertyConfig property = getOrInitializePropertyConfig(name)
                Map namedArgs = (Map) args[0]
                if(args[-1] instanceof Closure) {
                    PropertyConfig.configureExisting(
                            property,
                            ((Closure)args[-1])
                    )

                }
                PropertyConfig.configureExisting(property, namedArgs)
            }
            else {
                throw new MissingMethodException(name, getClass(), args)
            }
        }
        else {
            throw new MissingMethodException(name, getClass(), args)
        }
    }

    @Override
    protected PropertyConfig getOrInitializePropertyConfig(String name) {
        PropertyConfig pc = columns[name]
        if(pc == null && columns.containsKey('*')) {
            // apply global constraints constraints
            PropertyConfig globalConstraints = columns.get('*')
            if(globalConstraints != null) {
                pc = (PropertyConfig)globalConstraints.clone()
                if(pc.columns.size() == 1) {
                    pc.firstColumnIsColumnCopy = true
                }
            }
        }
        else {
            pc = columns[name]
        }
        if (pc == null) {
            pc = new PropertyConfig()
            columns[name] = pc
        }
        return pc
    }

    @Override
    protected PropertyConfig cloneGlobalConstraint() {
        // apply global constraints constraints
        PropertyConfig globalConstraints = columns.get('*')
        PropertyConfig cloned = (PropertyConfig) globalConstraints.clone()
        if (cloned.columns.size() == 1) {
            cloned.firstColumnIsColumnCopy = true
        }
        cloned
    }
}
