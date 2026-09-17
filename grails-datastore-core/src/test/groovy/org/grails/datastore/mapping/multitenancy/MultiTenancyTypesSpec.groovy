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
package org.grails.datastore.mapping.multitenancy

import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

import spock.lang.Specification

import org.grails.datastore.mapping.column.ColumnDatastore
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSourcesProvider
import org.grails.datastore.mapping.document.DocumentDatastore
import org.grails.datastore.mapping.graph.GraphDatastore
import org.grails.datastore.mapping.rdbms.RelationalDatastore

class MultiTenancyTypesSpec extends Specification {

    void "the tenant data source config annotation is a documented runtime type annotation"() {
        expect:
        TenantDataSourceConfig.getAnnotation(Retention).value() == RetentionPolicy.RUNTIME
        TenantDataSourceConfig.getAnnotation(Target).value() == [ElementType.TYPE] as ElementType[]
        TenantDataSourceConfig.getAnnotation(Documented) != null
        MtDefault.getAnnotation(TenantDataSourceConfig).dataSourcesToExclude() == [''] as String[]
        MtExcluding.getAnnotation(TenantDataSourceConfig).dataSourcesToExclude() == ['reporting', 'audit'] as String[]
    }

    void "multi tenant capable datastores extend the datastore and connection source abstractions"() {
        expect:
        Datastore.isAssignableFrom(MultiTenantCapableDatastore)
        ConnectionSourcesProvider.isAssignableFrom(MultiTenantCapableDatastore)
        MultiTenantCapableDatastore.isAssignableFrom(SchemaMultiTenantCapableDatastore)
        MultiTenantCapableDatastore.declaredMethods*.name.toSet() ==
                ['getMultiTenancyMode', 'getTenantResolver', 'getDatastoreForTenantId', 'withNewSession'] as Set
        SchemaMultiTenantCapableDatastore.declaredMethods*.name == ['addTenantForSchema']
    }

    void "datastore marker interfaces extend Datastore"() {
        expect:
        [ColumnDatastore, DocumentDatastore, GraphDatastore, RelationalDatastore].every {
            Datastore.isAssignableFrom(it) && it.declaredMethods.length == 0
        }
    }

}

@TenantDataSourceConfig
class MtDefault { }

@TenantDataSourceConfig(dataSourcesToExclude = ['reporting', 'audit'])
class MtExcluding { }
