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

package org.grails.datastore.mapping.dirty.checking

import groovy.transform.CompileStatic
import org.grails.datastore.mapping.proxy.EntityProxy

import jakarta.persistence.Transient

/**
 * Interface to classes that are able to track changes to their internal state.
 *
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@CompileStatic
trait DirtyCheckable {

    @Transient
    private transient Map<String, Object> $changedProperties

    /**
     * Indicates that the instance should start tacking changes. Note that if the instance is dirty this will clear any previously tracked
     * changes
     */
    void trackChanges() {
        $changedProperties = new LinkedHashMap<String, Object>()
    }

    /**
     * Sync the changes for a given instance with this instance.
     *
     * @param o a given object
     */
    void syncChangedProperties(Object o) {
        if (o instanceof DirtyCheckable) {
            o.trackChanges($changedProperties)
        }
    }

    /**
     * Initialises the changes with the given changes.
     *
     * @param changedProperties The changes.
     */
     void trackChanges(Map<String, Object> changedProperties) {
        $changedProperties = changedProperties
    }

    /**
     * @return True if the instance has any changes
     */
    boolean hasChanged() {
        if(this instanceof EntityProxy && !((EntityProxy)this).isInitialized()) {
            return false
        }
        else {
            return $changedProperties == null || DirtyCheckingSupport.DIRTY_CLASS_MARKER.is($changedProperties) || !$changedProperties.isEmpty()
        }
    }

    /**
     * @param propertyName The name of the property
     * @return True if the given property has any changes
     */
    boolean hasChanged(String propertyName) {
        if(this instanceof EntityProxy && !((EntityProxy)this).isInitialized()) {
            return false
        }
        else {
            return $changedProperties == null || DirtyCheckingSupport.DIRTY_CLASS_MARKER.is($changedProperties) || $changedProperties?.containsKey(propertyName)
        }
    }

    /**
     * Marks the whole class and all its properties as dirty. When called any future call to any of the hasChanged methods will return true.
     */
    void markDirty() {
        if( $changedProperties != null && $changedProperties.isEmpty()) {
            $changedProperties = DirtyCheckingSupport.DIRTY_CLASS_MARKER
        }
    }

    /**
     * Marks the given property name as dirty
     * @param propertyName The property name
     */
    void markDirty(String propertyName) {
        if( $changedProperties != null && !$changedProperties.containsKey(propertyName))  {
            if (DirtyCheckingSupport.DIRTY_CLASS_MARKER.is($changedProperties)) {
                trackChanges()
            }
            $changedProperties.put propertyName, ((GroovyObject)this).getProperty(propertyName)
        }
    }

    /**
     * Marks the given property name as dirty
     * @param propertyName The property name
     * @param newValue The new value
     */
    void markDirty(String propertyName, newValue) {
        if( $changedProperties != null && !$changedProperties.containsKey(propertyName))  {
            def oldValue = ((GroovyObject) this).getProperty(propertyName)
            markDirty(propertyName, newValue, oldValue)
        }
    }

    /**
     * Marks the given property name as dirty
     * @param propertyName The property name
     * @param newValue The new value
     */
    void markDirty(String propertyName, newValue, oldValue) {
        if( $changedProperties != null && !$changedProperties.containsKey(propertyName))  {
            boolean isNull = newValue == null
            if ((isNull && oldValue != null) ||
                    (!isNull && oldValue == null) ||
                    (!isNull && !newValue.equals(oldValue))) {
                if (DirtyCheckingSupport.DIRTY_CLASS_MARKER.is($changedProperties)) {
                    trackChanges()
                }
                $changedProperties.put propertyName, oldValue
            }
        }
    }

    /**
     * @return A list of the dirty property names
     */
    List<String> listDirtyPropertyNames() {
        if(this instanceof EntityProxy && !((EntityProxy)this).isInitialized()) {
            return Collections.emptyList()
        }

        if($changedProperties != null) {
            return Collections.unmodifiableList(
                $changedProperties.keySet().toList()
            )
        }
        return Collections.emptyList()
    }

    /**
     * Returns the original value of the property prior to when {@link #trackChanges()} was called
     *
     * @param propertyName The property name
     * @return The original value
     */
    Object getOriginalValue(String propertyName) {
        if($changedProperties != null && $changedProperties.containsKey(propertyName)) {
            return $changedProperties.get(propertyName)
        } else {
            return null
        }
    }
}
