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

package org.grails.testing.gorm.spock

import groovy.transform.CompileStatic

import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation
import org.springframework.transaction.support.TransactionSynchronizationManager

import grails.testing.gorm.DataTest
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.simple.SimpleMapDatastore

@CompileStatic
class DataTestSetupInterceptor implements IMethodInterceptor {

    @Override
    void intercept(IMethodInvocation invocation) throws Throwable {
        DataTest test = (DataTest) invocation.instance
        SimpleMapDatastore simpleDatastore = test.applicationContext.getBean(SimpleMapDatastore)
        test.currentSession = simpleDatastore.connect()
        DatastoreUtils.bindSession(test.currentSession)
        bindNonDefaultConnectionSessions(simpleDatastore)
        invocation.proceed()
    }

    /**
     * The single shared {@code GormRegistry} resolves a domain mapped to a non-default
     * {@code datasource} to a dedicated per-connection child datastore. Only the default datastore
     * had a thread-bound session, so operations on such a domain ran in a throwaway per-call session
     * and a {@code save()} without an explicit flush was discarded before an auto-flushing query
     * could observe it. Bind a session for every non-default connection source so those entities
     * share a stable session for the duration of the feature method.
     */
    private static void bindNonDefaultConnectionSessions(SimpleMapDatastore datastore) {
        for (ConnectionSource connectionSource : datastore.connectionSources.allConnectionSources) {
            String name = connectionSource.name
            if (ConnectionSource.DEFAULT == name) {
                continue
            }
            Datastore connectionDatastore = datastore.getDatastoreForConnection(name)
            if (connectionDatastore != null && TransactionSynchronizationManager.getResource(connectionDatastore) == null) {
                DatastoreUtils.bindSession(connectionDatastore.connect())
            }
        }
    }
}
