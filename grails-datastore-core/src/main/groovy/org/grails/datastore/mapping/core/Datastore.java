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

import groovy.lang.Closure;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;

import org.grails.datastore.mapping.config.UniqueNulls;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.services.ServiceRegistry;

/**
 * The <code>Datastore</code> interface is the basic common denominator all NoSQL databases should support:
 * <ul>
 *     <li>Storing data</li>
 *     <li>Retrieving one or more elements at a time, identified by their keys</li>
 *     <li>Deleting one or more elements</li>
 * </ul>
 *
 * @author Guillaume Laforge
 * @author Graeme Rocher
 */
public interface Datastore extends ServiceRegistry {

    /**
     * Connects to the datastore with the default connection details, normally provided via the datastore implementations constructor
     *
     * @return The session created using the default connection details.
     */
    Session connect();

    /**
     * Obtains the current session (if any)
     * @return The current thread bound session
     *
     * @throws ConnectionNotFoundException Thrown if the {@link #connect()} method has not yet been called
     */
    Session getCurrentSession() throws ConnectionNotFoundException;

    /**
     * Checks if there is a current session.
     * @return true if there's a bound active session
     */
    boolean hasCurrentSession();

    /**
     * Obtains the MappingContext object
     *
     * @return The MappingContext object
     */
    MappingContext getMappingContext();

    /**
     * Get the application event publisher.
     * @return the publisher
     */
    ApplicationEventPublisher getApplicationEventPublisher();

    /**
     * Get the application context.
     * @return the context
     */
    ConfigurableApplicationContext getApplicationContext();

    /**
     * Whether the datastore is schema-less. That is it allows changes to the schema runtime, dynamic attributes etc.
     *
     * @return True if it does
     */
    boolean isSchemaless();

    /**
     * How the {@code unique} constraint treats a {@code null} in a composite key for the entities of this datastore:
     * the {@code grails.gorm.unique.nulls} setting, whose default {@code database} reads
     * {@link #getDatabaseUniqueNulls()}. Never {@link UniqueNulls#DATABASE}.
     *
     * @return the reading
     */
    default UniqueNulls getUniqueNulls() {
        return getDatabaseUniqueNulls();
    }

    /**
     * How the unique index of the database this datastore keeps treats a {@code null} in a composite key: {@link UniqueNulls#SKIP}
     * where nulls are distinct, {@link UniqueNulls#VALUE} where they are compared. A datastore that keeps no such index,
     * or does not know the database, answers {@link UniqueNulls#VALUE}.
     *
     * @return {@link UniqueNulls#SKIP} or {@link UniqueNulls#VALUE}
     */
    default UniqueNulls getDatabaseUniqueNulls() {
        return UniqueNulls.VALUE;
    }

    /**
     * The configured {@link UniqueNulls} reading from settings, before resolving {@link UniqueNulls#DATABASE}.
     *
     * @return the configured mode, or {@code null} if unconfigured
     */
    default UniqueNulls getConfiguredUniqueNulls() {
        return null;
    }

    /**
     * Whether the unique index of the database this datastore keeps omits a key that is {@code null} in every
     * column, or the datastore does not have a unique index of its own (such as {@code MongoDatastore},
     * {@code Neo4jDatastore} or {@code SimpleMapDatastore}), so that two rows whose unique key is entirely null
     * do not conflict.
     *
     * @return {@code true} if entirely-null unique keys are exempt from uniqueness checks under the database reading
     */
    default boolean isEntirelyNullKeyExempt() {
        return true;
    }

    /**
     * Execute the given closure with a session
     *
     * @param callable The callable
     * @param <T> The return type
     * @return An instance of the return type
     */
    public <T> T withSession(Closure<T> callable);

}
