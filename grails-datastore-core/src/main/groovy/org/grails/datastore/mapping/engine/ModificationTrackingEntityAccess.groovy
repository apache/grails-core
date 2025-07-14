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

package org.grails.datastore.mapping.engine

import groovy.transform.CompileStatic

/**
 * Tracks modifications to the entity access, this allows synchronization of state for Hibernate for example
 *
 * @author Graeme Rocher
 * @since 6.0.9
 */
@CompileStatic
class ModificationTrackingEntityAccess implements EntityAccess {

    /**
     * The target entity access
     */
    final @Delegate
    EntityAccess target
    /**
     * The modified properties
     */
    final Map<String, Object> modifiedProperties = [:]

    ModificationTrackingEntityAccess(EntityAccess target) {
        this.target = target
    }

    @Override
    void setPropertyNoConversion(String name, Object value) {
        modifiedProperties.put(name, value)
        target.setPropertyNoConversion(name, value)
    }

    /**
     * Sets a property value
     * @param name The name of the property
     * @param value The value of the property
     */
    @Override
    void setProperty(String name, Object value) {
        modifiedProperties.put(name, value)
        target.setProperty(name, value)
    }

    @Override
    Object getProperty(String name) {
        target.getProperty(name)
    }
}
