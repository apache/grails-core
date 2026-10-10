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
import groovy.util.ConfigObject
import spock.lang.Specification
import spock.lang.Unroll

class GrailsClassUtilsSpec extends Specification {

    void 'getAllInterfacesForClass returns interfaces from the class and its superclasses'() {
        expect:
        GrailsClassUtils.getAllInterfacesForClass(GcuChild) as Set == [Runnable, Comparable, GcuChildMarker, GroovyObject] as Set
        GrailsClassUtils.getAllInterfacesForClass(Object).length == 0
    }

    void 'getAllInterfaces resolves interfaces from an instance'() {
        expect:
        GrailsClassUtils.getAllInterfaces(new GcuChild()) as Set == [Runnable, Comparable, GcuChildMarker, GroovyObject] as Set
    }

    void 'isVisible is true for a null classloader and reflects real visibility otherwise'() {
        expect:
        GrailsClassUtils.isVisible(String, null)
        GrailsClassUtils.isVisible(String, String.classLoader)
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

    void 'getBooleanFromMap resolves booleans, string booleans, and defaults'() {
        expect:
        GrailsClassUtils.getBooleanFromMap('flag', [flag: true])
        GrailsClassUtils.getBooleanFromMap('flag', [flag: 'true'])
        !GrailsClassUtils.getBooleanFromMap('flag', [flag: false])
        !GrailsClassUtils.getBooleanFromMap('missing', [:])
        GrailsClassUtils.getBooleanFromMap('missing', [:], true)
        !GrailsClassUtils.getBooleanFromMap('flag', null)
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

    void 'isClassBelowPackage matches classes in or below the given packages'() {
        expect:
        GrailsClassUtils.isClassBelowPackage(String, ['java.lang'])
        GrailsClassUtils.isClassBelowPackage(GcuChild, ['grails.util'])
        !GrailsClassUtils.isClassBelowPackage(String, ['java.util'])
    }

    void 'instantiateFromConfig and instantiateFromFlatConfig resolve the configured or default class'() {
        given:
        ConfigObject config = new ConfigObject()
        config.grails.gcu.impl = GcuInstanceHolder.name

        expect:
        GrailsClassUtils.instantiateFromConfig(config, 'grails.gcu.impl', GcuStaticHolder.name) instanceof GcuInstanceHolder
        GrailsClassUtils.instantiateFromFlatConfig([:], 'grails.gcu.impl', GcuStaticHolder.name) instanceof GcuStaticHolder
    }

    void 'hasBeenEnhancedForFeature reflects the Enhanced annotation enhancedFor list'() {
        expect:
        GrailsClassUtils.hasBeenEnhancedForFeature(GcuEnhanced, 'controller')
        !GrailsClassUtils.hasBeenEnhancedForFeature(GcuEnhanced, 'service')
        !GrailsClassUtils.hasBeenEnhancedForFeature(GcuInstanceHolder, 'controller')
    }

    void 'fastClass builds a working CGLIB FastClass for the given type'() {
        when:
        def fastClass = GrailsClassUtils.fastClass(GcuInstanceHolder)

        then:
        fastClass.newInstance() instanceof GcuInstanceHolder
    }
}

interface GcuChildMarker extends Runnable, Comparable<GcuChildMarker> {
}

class GcuBase {
    String baseName

    int compareTo(Object o) { 0 }
}

class GcuChild extends GcuBase implements GcuChildMarker {
    String childOnly

    @Override
    void run() { }
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

@Enhanced(version = '1.0', enhancedFor = ['controller'])
class GcuEnhanced {
}
