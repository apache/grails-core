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

import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.query.Query;

/**
 * @author Graeme Rocher
 * @since 3.0.7
 */
public interface QueryAliasAwareSession extends Session {

    /**
     * Creates a query instance for the give type
     *
     * @param type  The type
     * @param alias The alias to use in the query
     * @return The query
     */
    Query createQuery(Class type, String alias);
}
