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
package org.grails.datastore.mapping.core.connections

import org.springframework.core.env.PropertyResolver
import org.springframework.core.env.StandardEnvironment

import spock.lang.Specification

import org.grails.datastore.mapping.engine.types.CustomTypeMarshaller
import org.grails.datastore.mapping.multitenancy.TenantResolver

class AbstractConnectionSourceFactorySpec extends Specification {

    void "create(name, configuration) applies the injected TenantResolver to the fallback settings"() {
        given:
        def tenantResolver = Mock(TenantResolver)
        def factory = new RecordingConnectionSourceFactory()
        factory.setTenantResolver(tenantResolver)
        PropertyResolver configuration = new StandardEnvironment()

        when:
        def connectionSource = factory.create(ConnectionSource.DEFAULT, configuration)

        then:
        connectionSource.settings.multiTenancy.tenantResolver.is(tenantResolver)
    }

    void "create(name, configuration) applies the injected custom type marshallers to the fallback settings"() {
        given:
        def marshaller = Mock(CustomTypeMarshaller)
        def factory = new RecordingConnectionSourceFactory()
        factory.setCustomTypes([marshaller])
        PropertyResolver configuration = new StandardEnvironment()

        when:
        def connectionSource = factory.create(ConnectionSource.DEFAULT, configuration)

        then:
        connectionSource.settings.custom.types.contains(marshaller)
    }

    private static class RecordingConnectionSourceFactory extends AbstractConnectionSourceFactory<Object, ConnectionSourceSettings> {

        @Override
        ConnectionSource<Object, ConnectionSourceSettings> create(String name, ConnectionSourceSettings settings) {
            return new DefaultConnectionSource<Object, ConnectionSourceSettings>(name, new Object(), settings)
        }

        @Override
        Serializable getConnectionSourcesConfigurationKey() {
            return 'test.connections'
        }

        @Override
        protected <F extends ConnectionSourceSettings> ConnectionSourceSettings buildSettings(
                String name, PropertyResolver configuration, F fallbackSettings, boolean isDefaultDataSource) {
            return fallbackSettings
        }
    }
}
