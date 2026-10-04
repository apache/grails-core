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
package org.grails.datastore.mapping.model

import groovy.transform.CompileStatic

import org.grails.datastore.mapping.config.Property
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.Basic
import org.grails.datastore.mapping.model.types.Embedded
import org.grails.datastore.mapping.model.types.ManyToMany
import org.grails.datastore.mapping.model.types.ManyToOne
import org.grails.datastore.mapping.model.types.OneToMany
import org.grails.datastore.mapping.model.types.ToOne
import org.grails.datastore.mapping.reflect.EntityReflector

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
interface PersistentProperty<T extends Property> {

    /**
     * The name of the property
     * @return The property name
     */
    String getName()

    /**
     * The name with the first letter in upper case as per Java bean conventions
     * @return The capitilized name
     */
    String getCapitilizedName()

    /**
     * The type of the property
     * @return The property type
     */
    Class<?> getType()

    /**
     * Specifies the mapping between this property and an external form
     * such as a column, key/value pair, etc.
     *
     * @return The PropertyMapping instance
     */
    PropertyMapping<T> getMapping()

    default T getMappedForm() {
        PropertyMapping<T> mapping = Objects.requireNonNull(getMapping())
        return mapping.getMappedForm()
    }

    /**
     * Obtains the owner of this persistent property
     *
     * @return The owner
     */
    PersistentEntity getOwner()

    /**
     * Whether the property can be set to null
     *
     * @return True if it can
     */
    boolean isNullable()

    /**
     * @return Whether this property is inherited
     */
    boolean isInherited()

    /**
     * @return The reader for this property
     */
    EntityReflector.PropertyReader getReader()

    /**
     * @return The writer for this property
     */
    EntityReflector.PropertyWriter getWriter()

    default boolean isUnidirectionalOneToMany() {
        return ((this instanceof OneToMany) && !((Association<?>) this).isBidirectional())
    }

    default boolean isLazyAble() {
        return this instanceof ToOne && !(this instanceof Embedded) ||
                !(this instanceof Association) && !this.equals(this.getOwner().getIdentity())
    }

    default boolean isBidirectionalManyToOne() {
        if (this instanceof ManyToOne) {
            return ((ManyToOne) this).isBidirectional()
        }
        return false
    }

    default boolean supportsJoinColumnMapping() {
        return this instanceof ManyToMany || isUnidirectionalOneToMany() || this instanceof Basic
    }

    /**
     * Establish whether a collection property is sorted
     *
     * @return true if sorted
     */
    default boolean isSorted() {
        return SortedSet.isAssignableFrom(this.getType())
    }

    /**
     * @return Whether this property is part of a composite identifier
     */
    default boolean isCompositeIdProperty() {
        PersistentProperty[] compositeId = getOwner().getCompositeIdentity()
        if (compositeId != null) {
            for (PersistentProperty p in compositeId) {
                if (p.getName() == getName()) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * @return Whether this property is the identity
     */
    default boolean isIdentityProperty() {
        return getOwner().isIdentityName(getName())
    }

    default String getOwnerClassName() {
        PersistentEntity owner = getOwner()
        Class<?> javaClass = owner != null ? owner.getJavaClass() : null
        String className = javaClass != null ? javaClass.getName() : null
        if (className == null) {
            throw new IllegalMappingException('Property [' + getName() + '] has no owner entity defined')
        }
        return className
    }

}
