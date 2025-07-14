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
package org.grails.datastore.mapping.engine;

import org.grails.datastore.mapping.model.PersistentEntity;

/**
 * @author Graeme Rocher
 * @since 4.1
 *
 */
public interface EntityAccess {

    /**
     * @return The entity being persisted
     */
    Object getEntity();

    /**
     * Obtains a property value
     *
     * @param name the name of the value
     * @return The value of the property
     */
    Object getProperty(String name);

    /**
     * Obtains a property value
     *
     * @param name the name of the value
     * @return The value of the property
     */
    Object getPropertyValue(String name);

    /**
     * Obtains a property type
     *
     * @param name The name of the property
     * @return The type of of the property
     */
    Class getPropertyType(String name);

    /**
     * Sets a property value
     *
     * @param name  The name of the property
     * @param value The value of the property
     */
    void setProperty(String name, Object value);

    /**
     * @return Obtains the value of the entity identifier
     */
    Object getIdentifier();

    /**
     * Sets the entity identifier to the given value
     *
     * @param id The value to set
     */
    void setIdentifier(Object id);

    /**
     * Sets the entity identifier to the given value
     *
     * @param id The value to set
     */
    void setIdentifierNoConversion(Object id);

    /**
     * @return The name of the identifier property
     */
    String getIdentifierName();

    /**
     * @return The {@link org.grails.datastore.mapping.model.PersistentEntity} instance
     */
    PersistentEntity getPersistentEntity();

    /**
     * Refreshes the object from underlying entity state.
     */
    void refresh();

    /**
     * Sets a property without applying any automatic type conversion
     *
     * @param name  The name of the property
     * @param value The value
     */
    void setPropertyNoConversion(String name, Object value);
}
