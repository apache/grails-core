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

package org.grails.datastore.mapping.query.event;

import java.util.List;

import org.grails.datastore.mapping.query.Query;

/**
 * Query fired after a query has run.
 */
public class PostQueryEvent extends AbstractQueryEvent {
    /**
     * The results of the query.
     */
    private List results;

    public PostQueryEvent(Query query, List results) {
        super(query);
        this.results = results;
    }

    public PostQueryEvent(Object source, Query query, List results) {
        super(source, query);
        this.results = results;
    }

    /**
     * @return The results of the query. Note that this list is usually non-modifiable.
     */
    public List getResults() {
        return results;
    }

    /**
     * Reset the list of results to a new list. This allows an event handler to modify the results of a query.
     * @param results The replacement results.
     */
    public void setResults(List results) {
        if (results == null) {
            throw new IllegalArgumentException("results must be non-null");
        }
        this.results = results;
    }

    /**
     * @return The type of event.
     */
    @Override
    public QueryEventType getEventType() {
        return QueryEventType.PostExecution;
    }
}
