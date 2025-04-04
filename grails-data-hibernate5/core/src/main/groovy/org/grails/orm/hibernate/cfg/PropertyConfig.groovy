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

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovy.transform.builder.Builder
import groovy.transform.builder.SimpleStrategy
import org.grails.datastore.mapping.config.Property
import org.hibernate.FetchMode
import org.springframework.beans.MutablePropertyValues
import org.springframework.validation.DataBinder

import jakarta.persistence.FetchType

/**
 * Custom mapping for a single domain property. Note that a property
 * can have multiple columns via a component or a user type.
 *
 * @since 1.0.4
 * @author pledbrook
 */
@CompileStatic
@Builder(builderStrategy = SimpleStrategy, prefix = '')
class PropertyConfig extends Property {

    PropertyConfig() {
        setFetchStrategy(null)
    }

    @PackageScope
    // Whether the first column is created from cloning this instance
    boolean firstColumnIsColumnCopy = false

    boolean explicitSaveUpdateCascade;

    /**
     * The Hibernate type or user type of the property. This can be
     * a string or a class.
     */
    def type

    /**
     * The parameters for the property that can be used to
     * configure a Hibernate ParameterizedType implementation.
     */
    Properties typeParams

    /**
     * The default sort property name
     */
    String sort


    /**
     * The default sort order
     */
    String order

    /**
     * The batch size used for lazy loading
     */
    Integer batchSize


    /**
     * Whether to ignore ObjectNotFoundException
     */
    boolean ignoreNotFound = false

    /**
    * Whether or not this is column is insertable by hibernate
     */
    boolean insertable = true

    /**
    * Whether or not this column is updatable by hibernate
     */
    boolean updatable = true

    /**
     * Whether or not this column is updatable by hibernate
     *
     * @deprecated Use updatable instead
     */
    @Deprecated
    boolean getUpdateable() {
        return updatable
    }

    /**
     * Whether or not this column is updatable by hibernate
     * @deprecated Use updatable instead
     */
    @Deprecated
    void setUpdateable(boolean updateable) {
        this.updatable = updateable
    }

    /**
     * The columns
     */
    List<ColumnConfig> columns = []

    /**
     * Configure a column
     * @param columnDef The column definition
     * @return This property config
     */
    PropertyConfig column(@DelegatesTo(ColumnConfig) Closure columnDef) {
        if(columns.size() == 1 && firstColumnIsColumnCopy) {
            firstColumnIsColumnCopy = false
            ColumnConfig.configureExisting(columns[0], columnDef)
        }
        else {
            columns.add( ColumnConfig.configureNew(columnDef) )
        }
        return this
    }

    /**
     * Configure a column
     * @param columnDef The column definition
     * @return This property config
     */
    PropertyConfig column( Map columnDef ) {
        if(columns.size() == 1 && firstColumnIsColumnCopy) {
            firstColumnIsColumnCopy = false
            ColumnConfig.configureExisting(columns[0], columnDef)
        }
        else {
            columns.add( ColumnConfig.configureNew(columnDef) )
        }
        return this
    }

    /**
     * Configure a column
     * @param columnDef The column definition
     * @return This property config
     */
    PropertyConfig column( String columnDef ) {
        if(columns.size() == 1 && firstColumnIsColumnCopy) {
            firstColumnIsColumnCopy = false
            columns[0].name = columnDef
        }
        else {
            columns.add( ColumnConfig.configureNew(name: columnDef) )
        }
        return this
    }
    /**
     * The cache configuration
     */
    CacheConfig cache

    /**
     * Define the cache config
     * @param cacheConfig The cache config
     * @return This mapping
     */
    PropertyConfig cache(@DelegatesTo(CacheConfig) Closure cacheConfig) {
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
    PropertyConfig cache(Map cacheConfig) {
        if(this.cache == null) {
            this.cache = new CacheConfig()
        }
        CacheConfig.configureExisting(cache, cacheConfig)
        return this
    }

    /**
     * The join table configuration
     */
    JoinTable joinTable = new JoinTable()

    /**
     * The join table configuration
     */
    PropertyConfig joinTable(@DelegatesTo(JoinTable) Closure joinTableDef) {
        JoinTable.configureExisting(joinTable, joinTableDef)
        return this
    }

    /**
     * The join table configuration
     */
    PropertyConfig joinTable(String tableName) {
        joinTable.name = tableName
        return this
    }

    @Override
    void setUnique(boolean unique) {
        super.setUnique(unique)
        if(columns.size() == 1) {
            columns[0].unique = unique
        }
    }
    /**
     * The join table configuration
     */
    PropertyConfig joinTable(Map joinTableDef) {
        DataBinder dataBinder = new DataBinder(joinTable)
        dataBinder.bind(new MutablePropertyValues(joinTableDef))
        if(joinTableDef.key) {
            joinTable.key(joinTableDef.key.toString())
        }
        if(joinTableDef.column) {
            joinTable.column(joinTableDef.column.toString())
        }
        return this
    }

    /**
     * @param fetch The Hibernate {@link FetchMode}
     */
    void setFetch(FetchMode fetch) {
        if(FetchMode.JOIN.equals(fetch)) {
            super.setFetchStrategy(FetchType.EAGER)
        }
        else {
            super.setFetchStrategy(FetchType.LAZY)
        }
    }

    /**
     * @return The Hibernate {@link FetchMode}
     */
    FetchMode getFetchMode() {
        FetchType strategy = super.getFetchStrategy()
        if(strategy == null) {
            return FetchMode.DEFAULT
        }
        switch (strategy) {
            case FetchType.EAGER:
                return FetchMode.JOIN
            case FetchType.LAZY:
                return FetchMode.SELECT
            default:
                return FetchMode.DEFAULT
        }
    }
    /**
     * The column used to produce the index for index based collections (lists and maps)
     */
    PropertyConfig indexColumn

    /**
     * The column used to produce the index for index based collections (lists and maps)
     */
    PropertyConfig indexColumn(@DelegatesTo(PropertyConfig) Closure indexColumnConfig) {
        this.indexColumn = configureNew(indexColumnConfig)
        return this
    }

    /**
     * Configures a new PropertyConfig instance
     *
     * @param config The configuration
     * @return The new instance
     */
    static PropertyConfig configureNew(@DelegatesTo(PropertyConfig) Closure config) {
        PropertyConfig property = new PropertyConfig()
        return configureExisting(property, config)
    }


    /**
     * Configures a new PropertyConfig instance
     *
     * @param config The configuration
     * @return The new instance
     */
    static PropertyConfig configureNew(Map config) {
        PropertyConfig property = new PropertyConfig()
        return configureExisting(property, config)
    }

    /**
     * Configures an existing PropertyConfig instance
     *
     * @param config The configuration
     * @return The new instance
     */
    static PropertyConfig configureExisting(PropertyConfig property, Map config) {
        DataBinder dataBinder = new DataBinder(property)
        dataBinder.bind(new MutablePropertyValues(config))

        ColumnConfig cc
        if (property.columns) {
            cc = property.columns[0]
        }
        else {
            cc = new ColumnConfig()
            property.columns.add cc
        }
        if(config.column) {
            config.name = config.column
        }
        ColumnConfig.configureExisting(cc, config)

        return property
    }
    /**
     * Configures an existing PropertyConfig instance
     *
     * @param config The configuration
     * @return The new instance
     */
    static PropertyConfig configureExisting(PropertyConfig property, @DelegatesTo(PropertyConfig) Closure config) {
        config.setDelegate(property)
        config.setResolveStrategy(Closure.DELEGATE_ONLY)
        config.call()
        return property
    }

    /**
     * Shortcut to get the column name for this property.
     * @throws RuntimeException if this property maps to more than one
     * column.
     */
    String getColumn() {
        checkHasSingleColumn()
        if(columns.isEmpty()) return null
        return columns[0].name
    }

    String getEnumType() {
        checkHasSingleColumn()
        if(columns.isEmpty()) return "default"
        return columns[0].enumType
    }

    /**
     * Shortcut to get the SQL type of the corresponding column.
     * @throws RuntimeException if this property maps to more than one
     * column.
     */
    String getSqlType() {
        checkHasSingleColumn()
        if(columns.isEmpty()) return null
        return columns[0].sqlType
    }

    /**
     * Shortcut to get the index setting for this property's column.
     * @throws RuntimeException if this property maps to more than one
     * column.
     */
    String getIndexName() {
        checkHasSingleColumn()
        if(columns.isEmpty()) return null
        return columns[0].index?.toString()
    }

    /**
     * Shortcut to determine whether the property's column is configured
     * to be unique.
     * @throws RuntimeException if this property maps to more than one
     * column.
     */
    boolean isUnique() {
        if(columns.size()>1) {
            return super.isUnique()
        }
        else {
            if(columns.isEmpty()) return super.isUnique()
            return columns[0].unique
        }
    }

    /**
     * Shortcut to get the length of this property's column.
     * @throws RuntimeException if this property maps to more than one
     * column.
     */
    int getLength() {
        checkHasSingleColumn()
        if(columns.isEmpty()) return -1
        return columns[0].length
    }

    /**
     * Shortcut to get the precision of this property's column.
     * @throws RuntimeException if this property maps to more than one
     * column.
     */
    int getPrecision() {
        checkHasSingleColumn()
        if(columns.isEmpty()) return -1
        return columns[0].precision
    }

    /**
     * Shortcut to get the scale of this property's column.
     * @throws RuntimeException if this property maps to more than one
     * column.
     */
    int getScale() {
        checkHasSingleColumn()
        if(columns.isEmpty()) {
            return super.getScale()
        }
        return columns[0].scale
    }

    @Override
    void setScale(int scale) {
        checkHasSingleColumn()
        if(!columns.isEmpty())  {
            columns[0].scale = scale
        }
        else {
            super.setScale(scale)
        }
    }

    String toString() {
        "property[type:$type, lazy:$lazy, columns:$columns, insertable:${insertable}, updateable:${updatable}]"
    }

    protected void checkHasSingleColumn() {
        if (columns?.size() > 1) {
            throw new RuntimeException("Cannot treat multi-column property as a single-column property")
        }
    }

    @Override
    PropertyConfig clone() throws CloneNotSupportedException {
        PropertyConfig pc = (PropertyConfig)super.clone()

        pc.fetch = fetchMode
        pc.indexColumn = indexColumn != null ? (PropertyConfig)indexColumn.clone() : null
        pc.cache = cache != null ? cache.clone() : cache
        pc.joinTable = joinTable.clone()
        if(typeParams != null) {
            pc.typeParams = new Properties(typeParams)
        }

        List newColumns = new ArrayList<ColumnConfig>(columns.size())
        pc.columns = newColumns
        for(c in columns) {
            newColumns.add(c.clone())
        }
        return pc
    }
}
