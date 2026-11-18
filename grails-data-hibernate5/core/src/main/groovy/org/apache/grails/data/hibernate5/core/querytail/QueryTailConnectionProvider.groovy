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
package org.apache.grails.data.hibernate5.core.querytail

import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.sql.Connection

import groovy.transform.CompileStatic
import org.hibernate.boot.spi.SessionFactoryOptions
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.service.Service
import org.hibernate.service.ServiceRegistry
import org.hibernate.service.spi.ServiceRegistryImplementor
import org.hibernate.service.spi.SessionFactoryServiceContributor
import org.hibernate.service.spi.SessionFactoryServiceInitiator
import org.hibernate.service.spi.SessionFactoryServiceRegistryBuilder

import org.apache.grails.datamapping.core.querytail.QueryTailJdbcConnection
import org.apache.grails.datamapping.core.querytail.QueryTailRecorder

/**
 * Decorates the connection provider of a session factory: the one its service registry takes from the
 * registry the session factory is built with, whatever put it there, Hibernate's own initiator, or an
 * initiator or a service a configuration class added. The sessions of the session factory take their
 * connections from it, so the statements they run are recorded, while the schema work Hibernate does
 * through the registry the session factory is built with is not. Connections are captured at execution
 * and returned to their provider as the original instances. The provider stays the service of the
 * registry it came from, which configures, starts and stops it, so the decoration takes no part in its
 * lifecycle.
 *
 * @since 9.0
 */
@CompileStatic
final class QueryTailConnectionProvider<S extends Service> implements SessionFactoryServiceInitiator<S> {

    private final Class<S> role
    private final QueryTailRecorder recorder

    private QueryTailConnectionProvider(Class<S> role, QueryTailRecorder recorder) {
        this.role = role
        this.recorder = recorder
    }

    /**
     * @param recorder records the statements run on the connections the providers hand out
     * @return what adds the decorations of both connection providers, the one of a session factory and the
     * multi-tenant one, to the service registry of a session factory
     */
    static SessionFactoryServiceContributor contributor(QueryTailRecorder recorder) {
        new Contributor(recorder)
    }

    @Override
    Class<S> getServiceInitiated() {
        role
    }

    @Override
    S initiateService(SessionFactoryImplementor sessionFactory, SessionFactoryOptions sessionFactoryOptions,
            ServiceRegistryImplementor registry) {
        decorate(registry.parentServiceRegistry)
    }

    /**
     * @return the provider of the registry the session factory is built with, decorated, or null when it has none
     */
    private S decorate(ServiceRegistry built) {
        S provider = built?.getService(role)
        provider == null ? null : role.cast(Proxy.newProxyInstance(role.classLoader, [role] as Class<?>[], new Connections(provider, recorder)))
    }

    /**
     * Adds the decorations of the connection providers to the service registry of a session factory.
     */
    @CompileStatic
    private static final class Contributor implements SessionFactoryServiceContributor {

        private final QueryTailRecorder recorder

        Contributor(QueryTailRecorder recorder) {
            this.recorder = recorder
        }

        @Override
        void contribute(SessionFactoryServiceRegistryBuilder builder) {
            builder.addInitiator(new QueryTailConnectionProvider<ConnectionProvider>(ConnectionProvider, recorder))
            builder.addInitiator(new QueryTailConnectionProvider<MultiTenantConnectionProvider>(MultiTenantConnectionProvider, recorder))
        }
    }

    @CompileStatic
    private static final class Connections implements InvocationHandler {

        private final Service provider
        private final QueryTailRecorder recorder

        Connections(Service provider, QueryTailRecorder recorder) {
            this.provider = provider
            this.recorder = recorder
        }

        @Override
        Object invoke(Object proxy, Method method, Object[] args) {
            Object[] supplied = args == null ? null : args.clone()
            if (supplied != null) {
                for (int i = 0; i < supplied.length; i++) {
                    if (supplied[i] instanceof Connection) {
                        supplied[i] = QueryTailJdbcConnection.original((Connection) supplied[i])
                    }
                }
            }
            try {
                Object result = method.invoke(provider, supplied)
                // only once the session factory, created, has opened its capture
                result instanceof Connection && recorder.capture != null ?
                        QueryTailJdbcConnection.wrap((Connection) result, recorder) : result
            }
            catch (InvocationTargetException e) {
                throw e.cause
            }
        }
    }
}
