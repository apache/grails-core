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
package org.grails.datastore.gorm

import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSources
import org.grails.datastore.mapping.core.connections.ConnectionSourcesProvider
import spock.lang.Specification

/**
 * Tests for {@link ConnectionSourceNameResolver}
 *
 * @author Graeme Rocher
 */
class ConnectionSourceNameResolverSpec extends Specification {

    def "resolveConnectionSourceNames returns default when datastore is not a provider"() {
        given:
        Object datastore = new Object()

        when:
        List<String> names = ConnectionSourceNameResolver.resolveConnectionSourceNames(datastore)

        then:
        names == [ConnectionSource.DEFAULT]
    }

    def "resolveConnectionSourceNames returns default when connectionSources is null"() {
        given:
        ConnectionSourcesProvider provider = Mock {
            getConnectionSources() >> null
        }

        when:
        List<String> names = ConnectionSourceNameResolver.resolveConnectionSourceNames(provider)

        then:
        names == [ConnectionSource.DEFAULT]
    }

    def "resolveConnectionSourceNames returns names from collection"() {
        given:
        ConnectionSource cs1 = Mock { getName() >> 'db1' }
        ConnectionSource cs2 = Mock { getName() >> 'db2' }
        List<ConnectionSource> sources = [cs1, cs2]

        ConnectionSources connectionSources = Mock {
            getAllConnectionSources() >> sources
        }

        ConnectionSourcesProvider provider = Mock {
            getConnectionSources() >> connectionSources
        }

        when:
        List<String> names = ConnectionSourceNameResolver.resolveConnectionSourceNames(provider)

        then:
        names == ['db1', 'db2']
    }

    def "resolveConnectionSourceNames returns default when iterable is empty"() {
        given:
        ConnectionSources connectionSources = Mock {
            getAllConnectionSources() >> []
        }

        ConnectionSourcesProvider provider = Mock {
            getConnectionSources() >> connectionSources
        }

        when:
        List<String> names = ConnectionSourceNameResolver.resolveConnectionSourceNames(provider)

        then:
        names == [ConnectionSource.DEFAULT]
    }

    def "resolveConnectionSourceNames handles non-collection iterable"() {
        given:
        ConnectionSource cs1 = Mock { getName() >> 'db1' }
        ConnectionSource cs2 = Mock { getName() >> 'db2' }
        Iterable<ConnectionSource> iterable = [cs1, cs2] as Iterable

        ConnectionSources connectionSources = Mock {
            getAllConnectionSources() >> iterable
        }

        ConnectionSourcesProvider provider = Mock {
            getConnectionSources() >> connectionSources
        }

        when:
        List<String> names = ConnectionSourceNameResolver.resolveConnectionSourceNames(provider)

        then:
        names == ['db1', 'db2']
    }

    def "resolveDefaultConnectionSourceName returns default when datastore is not a provider"() {
        given:
        Object datastore = new Object()

        when:
        String name = ConnectionSourceNameResolver.resolveDefaultConnectionSourceName(datastore)

        then:
        name == ConnectionSource.DEFAULT
    }

    def "resolveDefaultConnectionSourceName returns default when connectionSources is null"() {
        given:
        ConnectionSourcesProvider provider = Mock {
            getConnectionSources() >> null
        }

        when:
        String name = ConnectionSourceNameResolver.resolveDefaultConnectionSourceName(provider)

        then:
        name == ConnectionSource.DEFAULT
    }

    def "resolveDefaultConnectionSourceName returns default connection source name"() {
        given:
        ConnectionSource defaultCs = Mock { getName() >> 'primary' }

        ConnectionSources connectionSources = Mock {
            getDefaultConnectionSource() >> defaultCs
        }

        ConnectionSourcesProvider provider = Mock {
            getConnectionSources() >> connectionSources
        }

        when:
        String name = ConnectionSourceNameResolver.resolveDefaultConnectionSourceName(provider)

        then:
        name == 'primary'
    }

    def "resolveDefaultConnectionSourceName returns default when default connection source is null"() {
        given:
        ConnectionSources connectionSources = Mock {
            getDefaultConnectionSource() >> null
        }

        ConnectionSourcesProvider provider = Mock {
            getConnectionSources() >> connectionSources
        }

        when:
        String name = ConnectionSourceNameResolver.resolveDefaultConnectionSourceName(provider)

        then:
        name == ConnectionSource.DEFAULT
    }
}
