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

import java.io.Serializable;
import java.util.List;

import org.grails.datastore.mapping.query.Query;

/**
 * A Persister is responsible for persisting and retrieving an object.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface Persister {

    /**
     * The type this persister persists
     *
     * @return The class this persisters persists
     */
    @SuppressWarnings("rawtypes")
    Class getType();

    /**
     * Persist an object using the given mapping context
     *
     * @param obj The object
     * @return A generated Key
     */
    Serializable persist(Object obj);

    /**
     * Persists a number of objects at the same time and
     * returns their keys in the order specified by the objs parameter
     *
     * @param objs The objects
     * @return A list of keys
     */
    List<Serializable> persist(@SuppressWarnings("rawtypes") Iterable objs);

    /**
     * Retrieves an object for the given context and Key
     *
     * @param key The key
     *
     * @return The object in question
     */
    Object retrieve(Serializable key);

    /**
     * Creates a proxy for the given key
     *
     * @param key The key
     * @return The proxy
     */
    Object proxy(Serializable key);

    /**
     * Deletes one or many objects
     * @param objects The objects to delete. Must all be of the same type or an exception will be thrown.
     */
    void delete(@SuppressWarnings("rawtypes") Iterable objects);

    /**
     * Batch retrieve several objects in one go
     *
     * @param keys The keys
     * @return The objects in a list in the same order as the specified keys
     */
    List<Object> retrieveAll(Iterable<Serializable> keys);

    /**
     * Deletes a single object
     * @param obj The object
     */
    void delete(Object obj);

    /**
     * Creates a query for the entity
     *
     * @return The Query object
     */
    Query createQuery();

    /**
     * Batch retrieve several objects in one go
     *
     * @param keys The keys
     * @return The objects in a list in the same order as the specified keys
     */
    List<Object> retrieveAll(Serializable[] keys);

    /**
     * Refreshes the given objects state
     * @param o The object to refresh
     * @return The objects id
     */
    Serializable refresh(Object o);

    /**
     * Get the identifier for the given object, if it has one.
     * @param o The object.
     * @return The object's id.
     */
    Serializable getObjectIdentifier(Object o);

    /**
     * Forces an insert of an object rather than trying to guess if it is an insert or an update
     * @param o The object
     * @return The id
     */
    Serializable insert(Object o);
}
