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

import java.util.List;

/**
 * An interface for classes that know how to query an association
 *
 * @author Graeme Rocher
 * @since 5.0
 */
public interface AssociationQueryExecutor<K, T> {

    /**
     * Queries the given primary key and returns the foreign keys
     *
     * @param primaryKey The primary key
     * @return The foreign keys
     */
    List<T> query(K primaryKey);

    /**
     * @return The entity to be queried
     */
    PersistentEntity getIndexedEntity();

    /**
     * @return Whether the query returns the keys for the entities or the enities themselves
     */
    boolean doesReturnKeys();
}
