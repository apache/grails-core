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

package org.grails.datastore.gorm.internal

import groovy.transform.CompileStatic
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSourcesProvider
import org.grails.datastore.mapping.model.DatastoreConfigurationException

/**
 * Utility methods to support AST transforms at runtime
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class RuntimeSupport {

    /**
     * Finds the default datastore from an array of datastores
     *
     * @param datastores The default datastore
     * @return
     */
    static Datastore findDefaultDatastore(Datastore[] datastores) {
        for(Datastore d in datastores) {
            if( d instanceof ConnectionSourcesProvider) {
                ConnectionSourcesProvider provider = (ConnectionSourcesProvider)d
                if(ConnectionSource.DEFAULT == provider.getConnectionSources().defaultConnectionSource.name) {
                    return d
                }
            }
        }
        if(datastores) {
            return datastores[0]
        }
        throw new DatastoreConfigurationException("No default datastore configured")
    }
}
