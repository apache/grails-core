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
package org.grails.config

import groovy.transform.CompileStatic
import spock.lang.Specification

class PrefixedConfigSpec extends Specification {

    void 'relative keys omit the prefix when reading the delegate'() {
        given:
        def delegate = new PropertySourcesConfig([
            'app.service.host': 'localhost',
            'app.service.port': 8080,
            host: 'outside'
        ])
        def config = new PrefixedConfig('app.service', delegate)

        expect:
        config.getAt('host') == 'localhost'
        config.get('port') == 8080
        config.getProperty('host') == 'localhost'
        config.getProperty('host', 'default') == 'localhost'
        config.getProperty('port', Integer) == 8080
        config.getProperty('port', Integer, 80) == 8080
        config.getRequiredProperty('host') == 'localhost'
        config.getRequiredProperty('port', Integer) == 8080
        config.containsKey('host')
        config.containsProperty('port')
        config.navigate('host') == 'localhost'
        config.size() == delegate.size()
        config.isEmpty() == delegate.isEmpty()
        config.values() == delegate.values()
        config.containsValue('localhost')
        !config.containsValue('absent')
    }

    void 'missing prefixed keys return null or the supplied default'() {
        given:
        def config = new PrefixedConfig('app.service', new PropertySourcesConfig([host: 'outside']))

        expect:
        config.getAt('host') == null
        config.getProperty('host') == null
        config.getProperty('host', 'default') == 'default'
        config.getProperty('port', Integer) == null
        config.getProperty('port', Integer, 80) == 80
        !config.containsKey('host')
        !config.containsProperty('port')
        config.navigate('missing') == null
    }

    void 'navigation prepends every token of a dotted prefix'() {
        given:
        def delegate = new PropertySourcesConfig([app: [service: [connection: [timeout: 30]]]])
        def config = new PrefixedConfig('app.service', delegate)

        expect:
        config.navigate('connection', 'timeout') == 30
        config.getProperty('connection.timeout', Integer) == 30
    }

    void 'missing required properties propagate delegate exceptions'() {
        given:
        def config = new PrefixedConfig('app', new PropertySourcesConfig())

        when:
        operation(config)

        then:
        thrown(IllegalStateException)

        where:
        operation << [
            { it.getRequiredProperty('missing') },
            { it.getRequiredProperty('missing', Integer) }
        ]
    }

    void 'equality and hash codes depend on the prefix and delegate'() {
        given:
        def delegate = new PropertySourcesConfig([name: 'Grails'])
        def config = new PrefixedConfig('app', delegate)
        def equivalent = new PrefixedConfig('app', delegate)

        expect:
        configsEqual(config, config)
        configsEqual(config, equivalent)
        config.hashCode() == equivalent.hashCode()
        !configsEqual(config, null)
        !configsEqual(config, delegate)
        !configsEqual(config, new PrefixedConfig('other', delegate))
        !configsEqual(config, new PrefixedConfig('app', new PropertySourcesConfig([name: 'other'])))
    }

    void 'modification and placeholder resolution are unsupported'() {
        given:
        def delegate = new PropertySourcesConfig(['app.name': 'original'])
        def config = new PrefixedConfig('app', delegate)

        when:
        operation(config)

        then:
        thrown(UnsupportedOperationException)
        delegate.getProperty('app.name') == 'original'

        where:
        operation << [
            { it.merge([name: 'changed']) },
            { it.setAt('name', 'changed') },
            { it.put('name', 'changed') },
            { it.remove('name') },
            { it.putAll([name: 'changed']) },
            { it.clear() },
            { it.resolvePlaceholders('${name}') },
            { it.resolveRequiredPlaceholders('${name}') }
        ]
    }

    @CompileStatic
    private static boolean configsEqual(PrefixedConfig config, Object other) {
        config.equals(other)
    }
}
