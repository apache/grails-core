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

import java.util.List;

/**
 * Responsible for creating indices for associations used in queries.
 * <p>
 * An instance may be specific to a particular association of a particular native instance of an entity (the parent
 * of the association).
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface AssociationIndexer<K, T> extends AssociationQueryExecutor<K, T> {

    /**
     * Creates an index queryable via the primary key. This is called *before* the entity that this association
     * indexer is part of is persisted, but after the native entry has been updated ready to be persisted.
     * This allows the index to be placed in the native instance itself, e.g. in a document database.
     * <p>
     * Usually, for a particular association type, only this OR {@link #index(Object, java.util.List)} will be
     * implemented.
     *
     * @param primaryKey  The primary key
     * @param foreignKeys The foreign keys
     */
    void preIndex(K primaryKey, List<T> foreignKeys);

    /**
     * Creates an index queryable via the primary key. This is called *after* the entity this association indexer
     * is part of has been persisted.
     * <p>
     * Usually, for a particular association type, only this OR {@link #preIndex(Object, java.util.List)} will be
     * implemented.
     *
     * @param primaryKey  The primary key
     * @param foreignKeys The foreign keys
     */
    void index(K primaryKey, List<T> foreignKeys);

    /**
     * Index a single foreign key
     *
     * @param primaryKey The primaryKey
     * @param foreignKey The foreignKey
     */
    void index(T primaryKey, K foreignKey);
}
