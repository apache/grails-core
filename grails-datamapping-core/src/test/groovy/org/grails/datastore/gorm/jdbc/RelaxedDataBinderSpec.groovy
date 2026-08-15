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
package org.grails.datastore.gorm.jdbc

import org.springframework.beans.MutablePropertyValues
import spock.lang.Specification

class RelaxedDataBinderSpec extends Specification {

    static class NestedBean {
        String value
    }

    static class SimpleBean {
        String fooBar
        String jdbcUrl
        Map<String, Object> options = [:]
        NestedBean nested
    }

    void "a dash separated property name is bound to the matching camelCase property"() {
        given:
        def target = new SimpleBean()
        def binder = new RelaxedDataBinder(target)
        def values = new MutablePropertyValues()
        values.add('foo-bar', 'baz')

        when:
        binder.bind(values)

        then:
        target.fooBar == 'baz'
    }

    void "an underscore separated property name is bound to the matching camelCase property"() {
        given:
        def target = new SimpleBean()
        def binder = new RelaxedDataBinder(target)
        def values = new MutablePropertyValues()
        values.add('foo_bar', 'baz')

        when:
        binder.bind(values)

        then:
        target.fooBar == 'baz'
    }

    void "withAlias resolves an alternate incoming property name to the target property"() {
        given:
        def target = new SimpleBean()
        def binder = new RelaxedDataBinder(target).withAlias('url', 'jdbcUrl')
        def values = new MutablePropertyValues()
        values.add('url', 'jdbc:h2:mem:test')

        when:
        binder.bind(values)

        then:
        target.jdbcUrl == 'jdbc:h2:mem:test'
    }

    void "binding to a Map target populates the map with the given keys"() {
        given:
        def map = [:]
        def binder = new RelaxedDataBinder(map)
        def values = new MutablePropertyValues()
        values.add('key', 'value')

        when:
        binder.bind(values)

        then:
        map.key == 'value'
    }

    void "a null nested bean property is auto-instantiated and populated"() {
        given:
        def target = new SimpleBean()
        def binder = new RelaxedDataBinder(target)
        def values = new MutablePropertyValues()
        values.add('nested.value', 'hello')

        when:
        binder.bind(values)

        then:
        target.nested != null
        target.nested.value == 'hello'
    }

    void "period separated keys are bound into an existing map property"() {
        given:
        def target = new SimpleBean()
        def binder = new RelaxedDataBinder(target)
        def values = new MutablePropertyValues()
        values.add('options.timeout', '30')
        values.add('options.retries', '3')

        when:
        binder.bind(values)

        then:
        target.options.timeout == '30'
        target.options.retries == '3'
    }
}
