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
package org.grails.datastore.mapping.reflect;

import org.grails.datastore.mapping.model.PersistentEntity;
import org.springframework.cglib.reflect.FastClass;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Used for reflective data
 *
 * @author Graeme Rocher
 * @since 5.0
 */
public interface EntityReflector {

    /**
     * The entity
     */
    PersistentEntity getPersitentEntity();

    /**
     * @return Obtains the dirty checking state for the given entity
     */
    Map<String, Object> getDirtyCheckingState(Object entity);

    /**
     * @return The fast class
     * @deprecated Do not use
     */
    @Deprecated
    FastClass fastClass();

    /**
     * @return The identity type
     */
    Class identifierType();

    /**
     * @return The name of the identifier
     */
    String getIdentifierName();

    /**
     * @return The property names
     */
    Iterable<String> getPropertyNames();

    /**
     * @return Obtain the identifier
     */
    Serializable getIdentifier(Object object);

    /**
     * Set the identifier
     *
     * @param value The value
     */
    void setIdentifier(Object object, Object value);

    /**
     * Get a property for the specified index
     *
     * @param object The object
     * @param index  The index
     * @return The value
     */
    Object getProperty(Object object, int index);

    /**
     * Set a property for the specified index
     *
     * @param object The object
     * @param index  The index
     * @param value  The value
     */
    void setProperty(Object object, int index, Object value);

    /**
     * Get a property for the specified index
     *
     * @param object The object
     * @param name   The index
     * @return The value
     */
    Object getProperty(Object object, String name);

    /**
     * Set a property for the specified index
     *
     * @param object The object
     * @param name   The index
     * @param value  The value
     */
    void setProperty(Object object, String name, Object value);

    /**
     * @param name Obtains the property reader for the given property
     * @return The name of the property
     */
    PropertyReader getPropertyReader(String name);

    /**
     * @param name Obtains the property writer for the given property
     * @return The property writer
     */
    PropertyWriter getPropertyWriter(String name);

    interface PropertyReader {

        /**
         * @return The field or null if the field cannot be resolved
         */
        Field field();

        /**
         * @return The getter
         */
        Method getter();

        /**
         * @return The property type
         */
        Class propertyType();

        /**
         * reads the property
         *
         * @param object The object
         * @return The read value
         */
        Object read(Object object);
    }

    interface PropertyWriter {

        /**
         * @return The field or null if the field cannot be resolved
         */
        Field field();

        /**
         * @return The getter
         */
        Method setter();

        /**
         * @return The property type
         */
        Class propertyType();

        /**
         * Writes the property
         *
         * @param object the object
         * @param value  The value
         */
        void write(Object object, Object value);
    }
}
