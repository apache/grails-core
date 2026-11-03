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
import spock.lang.Unroll

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

    @Unroll
    void 'isMatchBetweenPrimitiveAndWrapperTypes(#left, #right) == #expected'() {
        expect:
        GrailsClassUtils.isMatchBetweenPrimitiveAndWrapperTypes(left, right) == expected

        where:
        left           | right          || expected
        Integer        | int            || true
        int            | Integer        || true
        Integer        | long           || false
        String         | String         || false
    }

    @Unroll
    void 'isGroovyAssignableFrom(#left, #right) == #expected'() {
        expect:
        GrailsClassUtils.isGroovyAssignableFrom(left, right) == expected

        where:
        left    | right   || expected
        Object  | String  || true
        String  | String  || true
        Number  | int     || true
        int     | Integer || true
        String  | Integer || false
    }

    void 'isGroovyAssignableFrom throws when either argument is null'() {
        when:
        GrailsClassUtils.isGroovyAssignableFrom(null, String)

        then:
        thrown(NullPointerException)

        when:
        GrailsClassUtils.isGroovyAssignableFrom(String, null)

        then:
        thrown(NullPointerException)
    }

    void 'isStaticProperty is true for a public static getter or a public static field'() {
        expect:
        GrailsClassUtils.isStaticProperty(GcuStaticHolder, 'value')
        GrailsClassUtils.isStaticProperty(GcuStaticHolder, 'field')
        !GrailsClassUtils.isStaticProperty(GcuStaticHolder, 'instanceValue')
        !GrailsClassUtils.isStaticProperty(GcuStaticHolder, 'noSuchProperty')
    }

    void 'getStaticFieldValue reads a public static field and returns null when absent'() {
        expect:
        GrailsClassUtils.getStaticFieldValue(GcuStaticHolder, 'field') == 'fieldValue'
        GrailsClassUtils.getStaticFieldValue(GcuStaticHolder, 'noSuchField') == null
    }

    void 'getStaticPropertyValue prefers a static getter and falls back to a static field'() {
        expect:
        GrailsClassUtils.getStaticPropertyValue(GcuStaticHolder, 'value') == 'getterValue'
        GrailsClassUtils.getStaticPropertyValue(GcuStaticHolder, 'field') == 'fieldValue'
        GrailsClassUtils.getStaticPropertyValue(GcuStaticHolder, 'noSuchProperty') == null
    }

    void 'getPropertyOrStaticPropertyOrFieldValue checks instance property, then public field, then static'() {
        given:
        GcuInstanceHolder holder = new GcuInstanceHolder(name: 'Bob')

        expect:
        GrailsClassUtils.getPropertyOrStaticPropertyOrFieldValue(holder, 'name') == 'Bob'
        GrailsClassUtils.getPropertyOrStaticPropertyOrFieldValue(holder, 'publicField') == 'publicFieldValue'
        GrailsClassUtils.getPropertyOrStaticPropertyOrFieldValue(holder, 'value') == 'getterValue'
        GrailsClassUtils.getPropertyOrStaticPropertyOrFieldValue(holder, 'missing') == null
    }

    void 'getFieldValue and isPublicField reflect declared field visibility'() {
        given:
        GcuInstanceHolder holder = new GcuInstanceHolder(name: 'Bob')

        expect:
        GrailsClassUtils.isPublicField(holder, 'publicField')
        !GrailsClassUtils.isPublicField(holder, 'noSuchField')
        GrailsClassUtils.getFieldValue(holder, 'publicField') == 'publicFieldValue'
        GrailsClassUtils.getFieldValue(holder, 'noSuchField') == null
    }

    void 'isPropertyInherited is true only for a property declared on a superclass'() {
        expect:
        GrailsClassUtils.isPropertyInherited(GcuChild, 'baseName')
        !GrailsClassUtils.isPropertyInherited(GcuChild, 'childOnly')
        !GrailsClassUtils.isPropertyInherited(null, 'anything')
    }

    void 'createConcreteCollection creates the conventional implementation for each interface'() {
        expect:
        GrailsClassUtils.createConcreteCollection(List) instanceof ArrayList
        GrailsClassUtils.createConcreteCollection(Collection) instanceof ArrayList
        GrailsClassUtils.createConcreteCollection(SortedSet) instanceof TreeSet
        GrailsClassUtils.createConcreteCollection(Set) instanceof HashSet
    }

    @Unroll
    void 'isSetter(#name, #args) == #expected'() {
        expect:
        GrailsClassUtils.isSetter(name, args as Class[]) == expected

        where:
        name        | args           || expected
        'setName'   | [String]       || true
        'setName'   | [String, Long] || false
        'getName'   | [String]       || false
        ''          | [String]       || false
        'setName'   | null           || false
    }

    void 'isAssignableOrConvertibleFrom handles primitive conversion and plain assignability'() {
        expect:
        GrailsClassUtils.isAssignableOrConvertibleFrom(Number, int.class)
        GrailsClassUtils.isAssignableOrConvertibleFrom(CharSequence, String)
        !GrailsClassUtils.isAssignableOrConvertibleFrom(String, Integer)
        !GrailsClassUtils.isAssignableOrConvertibleFrom(null, String)
        !GrailsClassUtils.isAssignableOrConvertibleFrom(String, null)
    }

    void 'findPropertyNameForValue locates a property by reference equality of value'() {
        given:
        GcuInstanceHolder holder = new GcuInstanceHolder(name: 'Bob')

        expect:
        GrailsClassUtils.findPropertyNameForValue(holder, 'Bob') == 'name'
        GrailsClassUtils.findPropertyNameForValue(holder, 'nope') == null
    }

    void 'getGetterName, getSetterName, isGetter, and the property-for-accessor helpers delegate to GrailsNameUtils'() {
        expect:
        GrailsClassUtils.getGetterName('name') == 'getName'
        GrailsClassUtils.getSetterName('name') == 'setName'
        GrailsClassUtils.isGetter('getName', String, [] as Class[])
        GrailsClassUtils.getPropertyForGetter('getName', String) == 'name'
        GrailsClassUtils.getPropertyForGetter('getName', 'java.lang.String') == 'name'
        GrailsClassUtils.getPropertyForSetter('setName') == 'name'
    }

    void 'isPropertyGetter requires a public non-static getter shaped method'() {
        expect:
        GrailsClassUtils.isPropertyGetter(GcuInstanceHolder.getDeclaredMethod('getName'))
        !GrailsClassUtils.isPropertyGetter(GcuStaticHolder.getDeclaredMethod('getValue'))
    }

    void 'fastClass builds a working CGLIB FastClass for the given type'() {
        when:
        def fastClass = GrailsClassUtils.fastClass(GcuInstanceHolder)

        then:
        fastClass.newInstance() instanceof GcuInstanceHolder
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

class GcuBase {
    String baseName
}

class GcuChild extends GcuBase {
    String childOnly
}

class GcuStaticHolder {
    public static String field = 'fieldValue'
    String instanceValue

    static String getValue() {
        'getterValue'
    }
}

class GcuInstanceHolder {
    public String publicField = 'publicFieldValue'
    String name

    static String getValue() {
        'getterValue'
    }
}
