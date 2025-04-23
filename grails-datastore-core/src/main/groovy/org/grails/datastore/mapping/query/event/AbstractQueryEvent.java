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

import org.grails.datastore.mapping.query.Query;
import org.springframework.context.ApplicationEvent;

/**
 * Base class for query events.
 */
public abstract class AbstractQueryEvent extends ApplicationEvent {
    /**
     * The query.
     */
    protected Query query;

    public AbstractQueryEvent(Query query) {
        super(query.getSession().getDatastore());
        this.query = query;
    }

    public AbstractQueryEvent(Object source, Query query) {
        super(source);
        this.query = query;
    }

    /**
     * @return The type of event.
     */
    public abstract QueryEventType getEventType();

    /**
     * Get the query from the event.
     * @return The query.
     */
    public Query getQuery() {
        return query;
    }
}
