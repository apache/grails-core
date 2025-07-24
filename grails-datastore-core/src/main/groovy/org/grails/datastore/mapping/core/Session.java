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
package org.grails.datastore.mapping.core;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import jakarta.persistence.FlushModeType;

import org.springframework.transaction.TransactionDefinition;

import org.grails.datastore.mapping.engine.Persister;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.QueryCreator;
import org.grails.datastore.mapping.query.api.QueryableCriteria;
import org.grails.datastore.mapping.transactions.Transaction;

/**
 * The Session represents the active interaction with a datastore.
 *
 * @author Graeme Rocher
 * @author Guillaume Laforge
 *
 * @since 1.0
 */
@SuppressWarnings("rawtypes")
public interface Session extends QueryCreator {

    /**
     * Associates an attribute with the given persistent entity. Attributes will
     * be cleared out when the Session is closed or cleared.
     *
     * @param entity The persistent instance (must be associated with this Session)
     * @param attributeName The attribute name
     * @param value The value
     */
    void setAttribute(Object entity, String attributeName, Object value);

    /**
     * Obtains an attribute for the given entity
     *
     * @param entity The entity
     * @param attributeName The attribute
     * @return The attribute value
     */
    Object getAttribute(Object entity, String attributeName);

    /**
     * Set a property on this session. Note that properties are not cleared out when a session is cleared.
     * @param property The property name.
     * @param value The property value.
     * @return The previous property value, if there was one (or null).
     */
    Object setSessionProperty(String property, Object value);

    /**
     * Get the value of a property of the session.
     * @param property The name of the property.
     * @return The value.
     */
    Object getSessionProperty(String property);

    /**
     * Clear a property in a session.
     * @param property The property name.
     * @return The property value, if there was one (or null).
     */
    Object clearSessionProperty(String property);

    /**
     * @return <code>true</code> if connected to the datastore
     */
    boolean isConnected();

    /**
     * Disconnects from the datastore.
     */
    void disconnect();

    /**
     * Starts a transaction
     * @return The transaction
     */
    Transaction beginTransaction();

    /**
     * Starts a transaction
     * @return The transaction
     */
    Transaction beginTransaction(TransactionDefinition definition);

   /**
     * Obtains the MappingContext instance
     *
     * @return The MappingContext
     */
    MappingContext getMappingContext();

    /**
     * Stores and object and returns its key
     *
     * @param o The object
     * @return The the generated key
     */
    Serializable persist(Object o);

    /**
     * Forces an insert
     *
     * @param o The object
     * @return The id
     */
    Serializable insert(Object o);

    /**
     * Refreshes the given objects state
     * @param o The object to refresh
     */
    void refresh(Object o);

    /**
     * Attaches an object the current session
     * @param o The object to attach
     */
    void attach(Object o);

    /**
     * Flushes any pending changes to the datastore
     */
    void flush();

    /**
     * Clears any pending changes to the datastore
     */
    void clear();

    /**
     * Clear a specific object
     * @param o The object to clear
     */
    void clear(Object o);

    /**
     * Whether the object is contained within the first level cache
     * @param o The object to check
     * @return <code>true</code> if it is
     */
    boolean contains(Object o);

    /**
     * The flush mode, defaults to FlushModeType.COMMIT
     *
     * @param flushMode The FlushModeType
     */
    void setFlushMode(FlushModeType flushMode);

    /**
     * Obtains the current FlushModeType
     * @return The FlushModeType instance
     */
    FlushModeType getFlushMode();

    /**
     * Obtains a write lock on the given object
     *
     * @param o The object to lock
     */
    void lock(Object o);

    /**
     * Releases a lock, if not called all locked objects should be released by {@link #disconnect()}
     *
     * @param o The object to unlock
     */
    void unlock(Object o);

    /**
     * Persists several objects returning their identifiers in the order specified by the Iterable
     *
     * @param objects The Objects
     * @return The identifiers
     */
    List<Serializable> persist(Iterable objects);

    /**
     * Retrieves an individual object
     *
     * @param type The type
     * @param key The key
     * @return The object
     */
    <T> T retrieve(Class<T> type, Serializable key);

    /**
     * Retrieves a proxy for the given key
     *
     * @param type The type
     * @param key The key
     * @return The object
     */
    <T> T proxy(Class<T> type, Serializable key);

    /**
     * Retrieves an individual object, using a write lock to prevent loss of updates
     *
     * @param type The type
     * @param key The key
     * @return The object
     */
    <T> T lock(Class<T> type, Serializable key);

    /**
     * Deletes one or many objects
     *
     * @param objects The objects to delete
     */
    void delete(Iterable objects);

    /**
     * Deletes a single object
     * @param obj The object to delete
     */
    void delete(Object obj);

    /**
     * Deletes all objects matching the given criteria
     *
     * @param criteria The criteria
     * @return The total number of records deleted
     */
    long deleteAll(QueryableCriteria criteria);

    /**
     * Updates all objects matching the given criteria and property values
     * @param criteria The criteria
     * @param properties The properties
     * @return The total number of records updated
     */
    long updateAll(QueryableCriteria criteria, Map<String, Object> properties);

    /**
     * Retrieves several objects for the specified keys
     * @param type The type
     * @param keys The keys
     * @return A list of objects
     */
    List retrieveAll(Class type, Iterable keys);

    /**
     * Retrieves several objects for the specified keys
     * @param type The type
     * @param keys The keys
     * @return A list of objects
     */
    List retrieveAll(Class type, Serializable...keys);

    /**
     * Creates a query instance for the give type
     *
     * @param type The type
     * @return The query
     */
    Query createQuery(Class type);

    /**
     * @return The native interface to the datastore
     */
    Object getNativeInterface();

    /**
     * The persister for the given object
     * @param o The object
     * @return The persister
     */
    Persister getPersister(Object o);

    /**
     * Obtains the current transaction instance
     * @return The Transaction instance
     */
    Transaction getTransaction();

    /**
     * @return Whether the current session has an active transaction
     */
    boolean hasTransaction();

    /**
     * The Datastore that created this Session
     * @return The Datastore instance
     */
    Datastore getDatastore();

    /**
     * Check if the instance has been modified since loading.
     * @param instance the instance
     * @return <code>true</code> if one or more fields have changed
     */
    boolean isDirty(Object instance);

    /**
     * Obtains the identifier for the instance
     * @param instance The instance
     * @return The identifier or null if it cannot be established
     */
    Serializable getObjectIdentifier(Object instance);


    /**
     * Whether the session is synchronized with a transaction
     *
     * @param synchronizedWithTransaction True if it is
     */
    void setSynchronizedWithTransaction(boolean synchronizedWithTransaction);
}
