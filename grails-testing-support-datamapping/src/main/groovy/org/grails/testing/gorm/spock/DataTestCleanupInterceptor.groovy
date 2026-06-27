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
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import org.grails.datastore.mapping.transactions.SessionHolder

@CompileStatic
class DataTestCleanupInterceptor implements IMethodInterceptor {

    @Override
    void intercept(IMethodInvocation invocation) throws Throwable {
        cleanupDataTest((DataTest) invocation.instance)
        invocation.proceed()
    }

    void cleanupDataTest(DataTest testInstance) {
        SimpleMapDatastore simpleDatastore = testInstance.applicationContext.getBean(SimpleMapDatastore)
        unbindNonDefaultConnectionSessions(simpleDatastore)
        if (testInstance.currentSession != null) {
            testInstance.currentSession.disconnect()
            DatastoreUtils.unbindSession(testInstance.currentSession)
        }
        simpleDatastore.clearData()
    }

    /**
     * Symmetric to {@code DataTestSetupInterceptor.bindNonDefaultConnectionSessions}: disconnect and
     * unbind the per-connection sessions bound for non-default datasources so they do not leak into
     * the next feature method on the same thread.
     */
    private static void unbindNonDefaultConnectionSessions(SimpleMapDatastore datastore) {
        for (ConnectionSource connectionSource : datastore.connectionSources.allConnectionSources) {
            String name = connectionSource.name
            if (ConnectionSource.DEFAULT == name) {
                continue
            }
            Datastore connectionDatastore = datastore.getDatastoreForConnection(name)
            if (connectionDatastore == null) {
                continue
            }
            SessionHolder holder = (SessionHolder) TransactionSynchronizationManager.getResource(connectionDatastore)
            if (holder != null) {
                Session session = holder.session
                if (session != null) {
                    session.disconnect()
                    DatastoreUtils.unbindSession(session)
                }
            }
        }
    }
}
