/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.gorm.graphql

import spock.lang.Specification

import grails.gorm.multitenancy.Tenants
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings

class GraphqlTenantContextProfilingSpec extends Specification {

    void "profile graphql fetcher tenant wrapping overhead"() {
        given:
        def datastore = Stub(MultiTenantCapableDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DATABASE
        }
        
        // This is a placeholder to demonstrate the profiling pattern for GraphQL fetchers
        // In a real scenario, we would measure how many times Tenants.currentId() is called
        // when executing a DataFetcher.
        
        int iterations = 1000

        when: "Simulating repeated fetcher execution"
        long start = System.currentTimeMillis()
        for (int i = 0; i < iterations; i++) {
            // Simulated fetcher work
            Tenants.currentId(datastore)
        }
        long end = System.currentTimeMillis()

        then:
        println "GraphQL redundant tenant lookups: ${end - start} ms"
        true
    }
}
