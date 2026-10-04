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

import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.MutablePropertySources
import spock.lang.Specification

class CompositeConfigSpec extends Specification {

    private PropertySourcesConfig configOf(Map values, String name = 'test') {
        def propertySources = new MutablePropertySources()
        propertySources.addLast(new MapPropertySource(name, values))
        new PropertySourcesConfig(propertySources)
    }

    void 'addFirst gives its config the highest precedence'() {
        given:
        def low = configOf([key: 'low'])
        def high = configOf([key: 'high'])
        def composite = new CompositeConfig()

        when:
        composite.addFirst(low)
        composite.addFirst(high)

        then:
        composite.getProperty('key', String) == 'high'
    }

    void 'addLast gives its config the lowest precedence'() {
        given:
        def high = configOf([key: 'high'])
        def low = configOf([key: 'low'])
        def composite = new CompositeConfig()

        when:
        composite.addFirst(high)
        composite.addLast(low)

        then:
        composite.getProperty('key', String) == 'high'
        composite.getProperty('onlyInLow', String, null) == null
    }

    void 'a key missing from the first config falls through to the next'() {
        given:
        def composite = new CompositeConfig()
        composite.addFirst(configOf([a: '1']))
        composite.addLast(configOf([b: '2']))

        expect:
        composite.getProperty('a', String) == '1'
        composite.getProperty('b', String) == '2'
        composite.getProperty('missing', String) == null
    }

    void 'size, isEmpty, keySet, values and entrySet aggregate across all configs'() {
        given:
        def composite = new CompositeConfig()
        composite.addFirst(configOf([a: '1']))
        composite.addLast(configOf([b: '2']))

        expect:
        composite.size() == 2
        !composite.isEmpty()
        composite.keySet().containsAll(['a', 'b'])
        composite.values().containsAll(['1', '2'])
        composite.entrySet().size() == 2
        composite.containsKey('a')
        composite.containsValue('2')
        !composite.containsKey('missing')
    }

    void 'an empty composite reports isEmpty true'() {
        expect:
        new CompositeConfig().isEmpty()
        new CompositeConfig().size() == 0
    }

    void 'getProperty with a default value falls back when no config resolves the key'() {
        given:
        def composite = new CompositeConfig()
        composite.addFirst(configOf([a: '1']))

        expect:
        composite.getProperty('missing', String, 'default') == 'default'
        composite.getProperty('a', String, 'default') == '1'
    }

    void 'getRequiredProperty throws when the key cannot be resolved'() {
        given:
        def composite = new CompositeConfig()
        composite.addFirst(configOf([a: '1']))

        when:
        composite.getRequiredProperty('missing')

        then:
        thrown(IllegalStateException)

        when:
        String value = composite.getRequiredProperty('a')

        then:
        value == '1'
    }

    void 'mutating methods are unsupported since a composite cannot be modified'() {
        given:
        def composite = new CompositeConfig()

        when:
        composite.put('a', '1')

        then:
        thrown(UnsupportedOperationException)

        when:
        composite.remove('a')

        then:
        thrown(UnsupportedOperationException)

        when:
        composite.putAll([a: '1'])

        then:
        thrown(UnsupportedOperationException)

        when:
        composite.clear()

        then:
        thrown(UnsupportedOperationException)

        when:
        composite.merge([a: '1'])

        then:
        thrown(UnsupportedOperationException)

        when:
        composite.setAt('a', '1')

        then:
        thrown(UnsupportedOperationException)
    }

    void 'resolvePlaceholders and resolveRequiredPlaceholders are unsupported'() {
        given:
        def composite = new CompositeConfig()

        when:
        composite.resolvePlaceholders('${a}')

        then:
        thrown(UnsupportedOperationException)

        when:
        composite.resolveRequiredPlaceholders('${a}')

        then:
        thrown(UnsupportedOperationException)
    }

    void 'getAt returns the first non-null match by config precedence'() {
        given:
        def composite = new CompositeConfig()
        composite.addFirst(configOf([a: 'first']))
        composite.addLast(configOf([a: 'second']))

        expect:
        composite.getAt('a') == 'first'
        // a NavigableMapConfig-backed Config never returns null for getAt - a missing key
        // navigates to an empty (falsy) NavigableMap, not null, so getAt('missing') isn't
        // itself null and the composite loop returns it from the first config it tries.
        !composite.getAt('missing')
    }
}
