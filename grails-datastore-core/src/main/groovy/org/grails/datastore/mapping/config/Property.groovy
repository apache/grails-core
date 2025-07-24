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

import jakarta.persistence.AccessType
import jakarta.persistence.CascadeType
import jakarta.persistence.EnumType
import jakarta.persistence.FetchType

import org.springframework.beans.MutablePropertyValues
import org.springframework.validation.DataBinder

/**
 * Base class for classes returned from {@link org.grails.datastore.mapping.model.PropertyMapping#getMappedForm()}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
@Builder(builderStrategy = SimpleStrategy, prefix = '')
class Property implements Cloneable {

    /**
     * @return Whether this property is index
     */
    boolean index = false
    /**
     * @return Whether the property is nullable
     */
    boolean nullable = false

    /**
     * @return Whether this property is a database reference such as a foreign key
     */
    boolean reference = false
    /**
     * @return The strategy to use to fetch the property (lazy or eager)
     */
    FetchType fetchStrategy = FetchType.LAZY

    /**
     * Whether to use lazy proxies for each association. This has no effect if {@link #getFetchStrategy()} returns FetchType.EAGER, however
     * if FetchType is LAZY and lazy is set to true then for collection types each element of the collection will be a proxy.
     *
     * If lazy is false the collection will be fetched lazily, but fully initialized objects will be loaded for each element.
     *
     * @return Whether to use lazy proxies for collection elements
     */
    Boolean lazy = null
    /**
     * The target to map to, could be a database column, document attribute, or hash key
     *
     * @return The target name
     */
    String targetName
    /**
     * Set the id generator name or class.
     * @param generator name or class
     */
    String generator
    /**
     * @return The maximum size
     */
    Number maxSize = null
    /**
     * @return The minimum size
     */
    Number minSize = null
    /**
     * @return The maximum value
     */
    Comparable max = null
    /**
     * @return The minimum value
     */
    Comparable min = null
    /**
     * @return The scale
     */
    int scale = -1
    /**
     * @return List of valid values
     */
    List<String> inList = null
    /**
     * @return Whether the property is derived or not
     */
    boolean derived
    /**
     * Whether an entity of an orphaned association should be removed
     */
    boolean orphanRemoval = false
    /**
     * Cascading strategy for this property. Only makes sense if the
     * property is an association or collection.
     */
    String cascade
    /**
     * Cascade validation for associations that are not owned by the parent entity. Only makes sense
     * if the property is an association.
     */
    String cascadeValidate
    /**
     * For specifying the cascade type using {@link CascadeType}
     */
    List<CascadeType> cascades
    /**
     * The formula used to build the property
     */
    String formula
    /**
     * @return The default access type to use to read and write property values
     */
    AccessType accessType = AccessType.FIELD
    private boolean unique = false
    private List<String> uniquenessGroup = new ArrayList<String>()
    private String propertyName
    private EnumType enumType

    protected void setUniquenessGroup(List<String> uniquenessGroup) {
        this.uniquenessGroup = uniquenessGroup
    }

    Boolean getLazy() {
        return lazy
    }

    Boolean isLazy() {
        return lazy == Boolean.TRUE
    }

    void setLazy(Boolean lazy) {
        this.lazy = lazy
    }

    @Override
    Property clone() throws CloneNotSupportedException {
        Property cloned = (Property) super.clone()
        cloned.uniquenessGroup = new ArrayList<>(uniquenessGroup)
        if(inList != null) {
            cloned.inList = new ArrayList<>(inList)
        }

        return cloned
    }

    @CompileDynamic
    void setRange(ObjectRange range) {
        if (range != null) {
            this.max = range.getTo()
            this.min = range.getFrom()
        }
    }

    @CompileDynamic
    void setSize(IntRange maxSize) {
        if (maxSize != null) {
            this.maxSize = maxSize.getTo()
            this.minSize = maxSize.getFrom()
        }
    }

    /**
     * @return The name of the property this property mapping relates to
     */
    String getName() {
        return propertyName
    }

    void setName(String propertyName) {
        this.propertyName = propertyName
    }

    /**
     * @return The name of the property this property mapping relates to
     */
    Property name(String propertyName) {
        setName(propertyName)
        return this
    }

    /**
     * Makes it easier to configure the fetch strategy
     *
     * @param name The name of the fetch strategy
     */
    void setFetch(String name) {
        if (FetchType.EAGER.name().equalsIgnoreCase(name)) {
            setFetchStrategy(FetchType.EAGER)
        } else if ("select".equalsIgnoreCase(name)) {
            setFetchStrategy(FetchType.LAZY)
        } else if ("join".equalsIgnoreCase(name)) {
            setFetchStrategy(FetchType.EAGER)
        } else {
            setFetchStrategy(FetchType.LAZY)
        }
    }

    /**
     * Configure the fetch strategy
     *
     * @param name The name of the fetch strategy
     * @return
     */
    Property fetch(String name) {
        setFetch(name)
        return this
    }

    /**
     * Configure the fetch strategy
     *
     * @param type The type of the fetch strategy
     * @return
     */
    Property fetch(FetchType type) {
        setFetchStrategy(type)
        return this
    }
    /**
     * @return Whether the property should be unique
     */
    boolean isUnique() {
        return unique
    }

    /**
     * @return Whether the property is unique within a group
     */
    boolean isUniqueWithinGroup() {
        return !uniquenessGroup.isEmpty()
    }


    void setUnique(boolean unique) {
        this.unique = unique
    }

    void setUnique(String name) {
        setUnique(true)
        this.uniquenessGroup.add(name)
    }

    void setUnique(List<String> names) {
        setUnique(true)
        this.uniquenessGroup.addAll(names)
    }

    void unique(boolean unique) {
        setUnique(unique)
    }

    void unique(String name) {
        setUnique(name)
    }

    void unique(List<String> names) {
        setUnique(names)
    }

    List<String> getUniquenessGroup() {
        return uniquenessGroup
    }

    /**
     * @return The type of the enum, either ordinal or string
     */
    String getEnumType() {
        return enumType.toString()
    }

    /**
     * @return The type of the enum, either ordinal or string
     */
    EnumType getEnumTypeObject() {
        return enumType
    }

    void setEnumType(EnumType enumType) {
        this.enumType = enumType
    }

    void setEnumType(String enumType) {
        this.enumType = EnumType.valueOf(enumType.toUpperCase())
    }

    /**
     * Configures an existing PropertyConfig instance
     *
     * @param config The configuration
     * @return The new instance
     */
    static <T extends Property>  T configureExisting(T property, @DelegatesTo(Property) Closure config) {
        config.setDelegate(property)
        config.setResolveStrategy(Closure.DELEGATE_ONLY)
        config.call()
        return property
    }

    /**
     * Configures an existing PropertyConfig instance
     *
     * @param config The configuration
     * @return The new instance
     */
    static <T extends Property> T configureExisting(T property, Map config) {
        DataBinder dataBinder = new DataBinder(property)
        dataBinder.bind(new MutablePropertyValues(config))

        return property
    }
}
