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

import grails.util.Environment
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.MutablePropertySources
import spock.lang.Specification

class EnvironmentAwarePropertySourceSpec extends Specification {

    private final String envName = Environment.getCurrent().getName()

    private EnvironmentAwarePropertySource withSource(MutablePropertySources sources) {
        def envAware = new EnvironmentAwarePropertySource(sources)
        sources.addFirst(envAware)
        envAware
    }

    void 'a key nested under the current environment is exposed with the environment prefix stripped'() {
        given:
        def sources = new MutablePropertySources()
        Map<String, Object> values = [("environments.${envName}.foo.bar".toString()): 'baz']
        sources.addLast(new MapPropertySource('main', values))
        def envAware = withSource(sources)

        expect:
        envAware.getPropertyNames().contains('foo.bar')
        envAware.getProperty('foo.bar') == 'baz'
    }

    void 'a key not nested under the current environment is not exposed'() {
        given:
        def sources = new MutablePropertySources()
        sources.addLast(new MapPropertySource('main', [unrelated: 'value']))
        def envAware = withSource(sources)

        expect:
        !envAware.getPropertyNames().contains('unrelated')
        envAware.getProperty('unrelated') == null
    }

    void 'a source whose name contains "plugin" is excluded from environment-aware resolution'() {
        given: 'a plugin-named source with an environment-nested key (GRAILS-12123)'
        def sources = new MutablePropertySources()
        Map<String, Object> values = [("environments.${envName}.foo".toString()): 'from-plugin']
        sources.addLast(new MapPropertySource('some-plugin-config', values))
        def envAware = withSource(sources)

        expect:
        !envAware.getPropertyNames().contains('foo')
        envAware.getProperty('foo') == null
    }

    void 'a non-enumerable property source is ignored when scanning for environment keys'() {
        given:
        def sources = new MutablePropertySources()
        sources.addLast(new org.springframework.core.env.PropertySource<Object>('opaque', new Object()) {
            @Override
            Object getProperty(String name) {
                throw new UnsupportedOperationException('should never be queried by name scanning')
            }
        })
        def envAware = withSource(sources)

        expect:
        envAware.getPropertyNames().length == 0
    }

    void 'an empty PropertySources yields no environment-aware property names'() {
        given:
        def envAware = withSource(new MutablePropertySources())

        expect:
        envAware.getPropertyNames().length == 0
    }
}
