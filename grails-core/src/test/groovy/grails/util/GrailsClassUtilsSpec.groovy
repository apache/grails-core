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
package grails.util

import grails.artefact.Enhanced
import spock.lang.Specification

class GrailsClassUtilsSpec extends Specification {

    void 'interface discovery includes superclass and superinterface declarations'() {
        given:
        def instance = new ByteArrayInputStream(new byte[0])
        def expected = [Closeable, AutoCloseable] as Set

        expect:
        (GrailsClassUtils.getAllInterfaces(instance) as Set) == expected
        GrailsClassUtils.getAllInterfacesAsSet(instance) == expected
        (GrailsClassUtils.getAllInterfacesForClass(ByteArrayInputStream) as Set) == expected
        (GrailsClassUtils.getAllInterfacesForClass(ByteArrayInputStream, getClass().classLoader) as Set) == expected
        GrailsClassUtils.getAllInterfacesForClassAsSet(ByteArrayInputStream) == expected
        GrailsClassUtils.getAllInterfacesForClassAsSet(ByteArrayInputStream, getClass().classLoader) == expected
        GrailsClassUtils.getAllInterfacesForClass(Object).length == 0
    }

    void 'interface discovery rejects null arguments'() {
        when:
        operation()

        then:
        thrown(IllegalArgumentException)

        where:
        operation << [
            { GrailsClassUtils.getAllInterfaces(null) },
            { GrailsClassUtils.getAllInterfacesAsSet(null) },
            { GrailsClassUtils.getAllInterfacesForClassAsSet(null) }
        ]
    }

    void 'visibility checks accept visible classes and reject unavailable classes'() {
        given:
        def bootstrapOnly = new ClassLoader(null) { }

        expect:
        GrailsClassUtils.isVisible(TestBean, null)
        GrailsClassUtils.isVisible(TestBean, getClass().classLoader)
        GrailsClassUtils.isVisible(String, bootstrapOnly)
        !GrailsClassUtils.isVisible(TestBean, bootstrapOnly)
    }

    void 'property descriptors expose property types and exact type matching'() {
        expect:
        GrailsClassUtils.getPropertyType(TestBean, 'name') == String
        GrailsClassUtils.getPropertyType(TestBean, 'missing') == null
        GrailsClassUtils.getPropertyType(null, 'name') == null
        GrailsClassUtils.getPropertyType(TestBean, ' ') == null
        GrailsClassUtils.isPropertyOfType(TestBean, 'name', String)
        !GrailsClassUtils.isPropertyOfType(TestBean, 'name', CharSequence)
        !GrailsClassUtils.isPropertyOfType(TestBean, 'missing', String)
        GrailsClassUtils.getProperty(TestBean, 'name').propertyType == String
        GrailsClassUtils.getProperty(TestBean, 'name', String).name == 'name'
        GrailsClassUtils.getProperty(TestBean, 'name', Integer) == null
        GrailsClassUtils.getProperty(TestBean, 'missing') == null
        GrailsClassUtils.getProperty(null, 'name') == null
        GrailsClassUtils.getProperty(TestBean, null) == null
        GrailsClassUtils.getProperty(TestBean, 'name', null) == null
    }

    void 'property type selection distinguishes assignable properties from accepting properties'() {
        expect:
        GrailsClassUtils.getPropertiesOfType(TestBean, String)*.name.toSet() == ['name', 'text'] as Set
        GrailsClassUtils.getPropertiesAssignableToType(TestBean, String)*.name == ['name']
        GrailsClassUtils.getPropertiesAssignableToType(TestBean, CharSequence)*.name.toSet() == ['name', 'text'] as Set
        GrailsClassUtils.getPropertiesOfType(null, String).length == 0
        GrailsClassUtils.getPropertiesOfType(TestBean, null).length == 0
        GrailsClassUtils.getPropertiesAssignableToType(null, String).length == 0
        GrailsClassUtils.getPropertiesAssignableToType(TestBean, null).length == 0
    }

    void 'new instance property lookup handles valid, invalid and uninstantiable classes'() {
        expect:
        GrailsClassUtils.getPropertyValueOfNewInstance(TestBean, 'name') == 'Grails'
        GrailsClassUtils.getPropertyValueOfNewInstance(TestBean, 'name', String) == 'Grails'
        GrailsClassUtils.getPropertyValueOfNewInstance(TestBean, 'missing') == null
        GrailsClassUtils.getPropertyValueOfNewInstance(null, 'name') == null
        GrailsClassUtils.getPropertyValueOfNewInstance(TestBean, ' ') == null
        GrailsClassUtils.getPropertyValueOfNewInstance(Runnable, 'name') == null
        GrailsClassUtils.getPropertyValueOfNewInstance(Runnable, 'name', String) == null
    }

    void 'property descriptor lookup finds matching values or returns null'() {
        given:
        def bean = new TestBean()

        expect:
        GrailsClassUtils.getPropertyDescriptorForValue(bean, 'Grails').name == 'name'
        GrailsClassUtils.getPropertyDescriptorForValue(bean, 'missing') == null
        GrailsClassUtils.getPropertyDescriptorForValue(null, 'Grails') == null
        GrailsClassUtils.getPropertyDescriptorForValue(bean, null) == null
    }

    void 'collection conversion preserves order and accepts null collections'() {
        expect:
        GrailsClassUtils.collectionToObjectArray(['one', 2, null]).toList() == ['one', 2, null]
        GrailsClassUtils.collectionToObjectArray([]).length == 0
        GrailsClassUtils.collectionToObjectArray(null).length == 0
    }

    void 'boolean map lookup converts values and respects defaults'() {
        expect:
        GrailsClassUtils.getBooleanFromMap('enabled', values, defaultValue) == expected
        GrailsClassUtils.getBooleanFromMap('enabled', values) == withoutDefault

        where:
        values                | defaultValue || expected | withoutDefault
        null                  | true         || true     | false
        [:]                   | true         || true     | false
        [enabled: null]       | true         || true     | false
        [enabled: true]       | false        || true     | true
        [enabled: false]      | true         || false    | false
        [enabled: 'true']     | false        || true     | true
        [enabled: 'TRUE']     | false        || true     | true
        [enabled: 'false']    | true         || false    | false
        [enabled: 'anything'] | true         || false    | false
    }

    void 'package matching accepts containing packages and skips null entries'() {
        expect:
        GrailsClassUtils.isClassBelowPackage(GrailsClassUtils, ['grails.util'])
        GrailsClassUtils.isClassBelowPackage(GrailsClassUtils, [null, 'grails'])
        !GrailsClassUtils.isClassBelowPackage(GrailsClassUtils, ['org.grails'])
        !GrailsClassUtils.isClassBelowPackage(GrailsClassUtils, [])
    }

    void 'flat configuration selects an implementation or uses the default'() {
        expect:
        GrailsClassUtils.instantiateFromFlatConfig([:], 'implementation', ArrayList.name) instanceof ArrayList
        GrailsClassUtils.instantiateFromFlatConfig([implementation: LinkedHashSet.name], 'implementation', ArrayList.name) instanceof LinkedHashSet
        GrailsClassUtils.instantiateFromFlatConfig([implementation: new StringBuilder(LinkedHashSet.name)], 'implementation', ArrayList.name) instanceof LinkedHashSet
        GrailsClassUtils.instantiateFromFlatConfig([implementation: 42], 'implementation', ArrayList.name) instanceof ArrayList
    }

    void 'nested configuration is flattened before instantiation'() {
        given:
        def config = new ConfigObject()
        config.service.implementation = LinkedHashSet.name

        expect:
        GrailsClassUtils.instantiateFromConfig(config, 'service.implementation', ArrayList.name) instanceof LinkedHashSet
    }

    void 'unknown implementation classes propagate class loading failures'() {
        when:
        GrailsClassUtils.instantiateFromFlatConfig([implementation: 'grails.util.MissingImplementation'], 'implementation', ArrayList.name)

        then:
        thrown(ClassNotFoundException)
    }

    void 'feature enhancement requires an annotation containing the feature'() {
        expect:
        GrailsClassUtils.hasBeenEnhancedForFeature(EnhancedBean, 'controllers')
        !GrailsClassUtils.hasBeenEnhancedForFeature(EnhancedBean, 'services')
        !GrailsClassUtils.hasBeenEnhancedForFeature(TestBean, 'controllers')
    }

    static class TestBean {
        String name = 'Grails'
        CharSequence text = 'text'
        Object value
    }

    @Enhanced(version = '7.0', enhancedFor = ['controllers'])
    static class EnhancedBean {
    }
}
