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

import grails.config.Config
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.MutablePropertySources
import spock.lang.Specification

class PrefixedConfigSpec extends Specification {

    private PropertySourcesConfig configOf(Map values) {
        def propertySources = new MutablePropertySources()
        propertySources.addLast(new MapPropertySource('test', values))
        new PropertySourcesConfig(propertySources)
    }

    void 'getProperty prefixes the key before delegating'() {
        given:
        def delegate = configOf(['grails.foo.bar': 'baz'])
        def prefixed = new PrefixedConfig('grails.foo', delegate)

        expect:
        prefixed.getProperty('bar', String) == 'baz'
        prefixed.getProperty('bar') == 'baz'
        prefixed.getProperty('missing', String, 'default') == 'default'
    }

    void 'containsProperty and get delegate with the prefixed key'() {
        given:
        def delegate = configOf(['grails.foo.bar': 'baz'])
        def prefixed = new PrefixedConfig('grails.foo', delegate)

        expect:
        prefixed.containsProperty('bar')
        !prefixed.containsProperty('missing')
        prefixed.get('bar') == 'baz'
    }

    void 'getRequiredProperty throws when the prefixed key cannot be resolved'() {
        given:
        def delegate = configOf(['grails.foo.bar': 'baz'])
        def prefixed = new PrefixedConfig('grails.foo', delegate)

        when:
        prefixed.getRequiredProperty('missing')

        then:
        thrown(IllegalStateException)

        when:
        String value = prefixed.getRequiredProperty('bar')

        then:
        value == 'baz'
    }

    void 'keySet strips and re-adds the prefix consistently'() {
        given:
        def delegate = new PropertySourcesConfig()
        delegate.put('grails.foo.bar', 'baz')
        def prefixed = new PrefixedConfig('grails', delegate)

        expect:
        prefixed.keySet().every { it.startsWith('grails.') }
    }

    void 'entrySet remaps each entry key with the prefix and preserves the value'() {
        given:
        def delegate = configOf(['bar': 'baz'])
        def prefixed = new PrefixedConfig('grails', delegate)

        when:
        def entries = prefixed.entrySet()

        then:
        entries.size() == 1
        entries.first().key == 'grails.bar'
        entries.first().value == 'baz'
    }

    void 'equals(Object) reflects its own identity/prefix/delegate logic when invoked directly, bypassing Groovy Map-equality interception'() {
        // PrefixedConfig implements java.util.Map (via Config), so Groovy's own == / .equals()
        // dispatch for a Map-typed receiver routes through Groovy's Map-content comparison
        // machinery rather than reaching this class's equals(Object) override - this is true
        // for ANY Map-implementing Config, pre-existing behavior unrelated to source language.
        // Invoking the declared method via reflection bypasses that interception and exercises
        // the real override directly.
        given:
        def delegate1 = configOf(['bar': 'baz'])
        def a = new PrefixedConfig('grails', delegate1)
        def b = new PrefixedConfig('grails', delegate1)
        def c = new PrefixedConfig('other', delegate1)
        def equalsMethod = PrefixedConfig.class.getMethod('equals', Object)

        expect:
        equalsMethod.invoke(a, a)
        equalsMethod.invoke(a, b)
        !equalsMethod.invoke(a, c)
        !(equalsMethod.invoke(a, [null] as Object[]) as Boolean)
        !equalsMethod.invoke(a, 'not a config')
    }

    void 'mutating methods are unsupported since a prefixed config cannot be modified'() {
        given:
        def prefixed = new PrefixedConfig('grails', configOf([:]))

        when:
        prefixed.put('a', '1')

        then:
        thrown(UnsupportedOperationException)

        when:
        prefixed.remove('a')

        then:
        thrown(UnsupportedOperationException)

        when:
        prefixed.putAll([a: '1'])

        then:
        thrown(UnsupportedOperationException)

        when:
        prefixed.clear()

        then:
        thrown(UnsupportedOperationException)

        when:
        prefixed.merge([a: '1'])

        then:
        thrown(UnsupportedOperationException)

        when:
        prefixed.setAt('a', '1')

        then:
        thrown(UnsupportedOperationException)
    }

    void 'resolvePlaceholders and resolveRequiredPlaceholders are unsupported'() {
        given:
        def prefixed = new PrefixedConfig('grails', configOf([:]))

        when:
        prefixed.resolvePlaceholders('${a}')

        then:
        thrown(UnsupportedOperationException)

        when:
        prefixed.resolveRequiredPlaceholders('${a}')

        then:
        thrown(UnsupportedOperationException)
    }
}
