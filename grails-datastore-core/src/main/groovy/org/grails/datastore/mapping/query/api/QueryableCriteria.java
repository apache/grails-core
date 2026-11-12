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
package org.grails.datastore.mapping.query.api;

import java.util.List;

import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.query.Query;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
public interface QueryableCriteria<T> extends Criteria {

    /**
     * @return The target entity
     */
    PersistentEntity getPersistentEntity();

    /**
     * @return A list of all criteria
     */
    List<Query.Criterion> getCriteria();

    List<Query.Projection> getProjections();

    /**
     * @return Find a single result
     */
    T find();

    /**
     * List all results
     * @return All results
     */
    List<T> list();

    /**
     * @return The alias to be used for the query, null if none
     */
    String getAlias();

    /**
     * Returns the criteria to read the query from, with its deferred criteria applied: the deferred query runs once,
     * the first time the criteria is used, and what it adds stays part of the criteria. A criteria with a deferred
     * query returns a copy, so that a query implementation can extend or translate it without changing the
     * criteria it was given. A query implementation calls this before it translates a criteria that appears as a
     * subquery. Implementations without deferred criteria return themselves.
     *
     * @return The criteria to read the query from
     */
    default QueryableCriteria<T> materializeLazyCriteria() {
        return this;
    }

    /**
     * @return The default maximum number of results set with {@code max(int)}, or null when none was set
     */
    default Integer getDefaultMax() {
        return null;
    }

    /**
     * @return The default offset set with {@code offset(int)}, or null when none was set
     */
    default Integer getDefaultOffset() {
        return null;
    }

}
