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

package org.grails.datastore.mapping.multitenancy;

import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings;

/**
 * For datastores that are capable of implementing the addition of new schemas at runtime for a single shared database instance
 *
 * @author Graeme Rocher
 * @since 6.1
 */
public interface SchemaMultiTenantCapableDatastore<T, S extends ConnectionSourceSettings> extends MultiTenantCapableDatastore<T, S> {

    /**
     * Add a new tenant at runtime for the given schema name
     *
     * @param schemaName The schema name
     */
    void addTenantForSchema(String schemaName);
}
