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

import spock.lang.Specification

class GrailsMetaClassUtilsSpec extends Specification {
    static {
        ExpandoMetaClass.enableGlobally()
    }
        
    def "delegating metaclass shouldn't be replaced"() {
        given:
        def obj = new MySampleClass(name: 'John Doe')
        def dmc = new DelegatingMetaClass(MySampleClass)
        obj.metaClass = dmc
        when:
        MetaClass mc = GrailsMetaClassUtils.getMetaClass(obj)
        then:
        mc instanceof ExpandoMetaClass
        obj.getMetaClass().getAdaptee() == dmc
    }

    void 'getMetaClass on a plain non-GroovyObject instance returns the class-wide expando metaclass'() {
        given:
        String plainJavaObject = 'not a GroovyObject wrapper target, but getMetaClass handles any Object'

        expect:
        GrailsMetaClassUtils.getMetaClass(plainJavaObject) instanceof ExpandoMetaClass
    }

    void 'getRegistry returns the shared Groovy MetaClassRegistry'() {
        expect:
        GrailsMetaClassUtils.registry.is(GroovySystem.metaClassRegistry)
    }

    void 'getExpandoMetaClass returns and permanently registers an ExpandoMetaClass for the given class'() {
        when:
        ExpandoMetaClass emc = GrailsMetaClassUtils.getExpandoMetaClass(MetaClassSampleClass)

        then:
        emc != null
        GroovySystem.metaClassRegistry.getMetaClass(MetaClassSampleClass).is(emc)
    }

    void 'copyExpandoMetaClass copies dynamic methods and properties from one class to another'() {
        given:
        MetaClassSampleClass.metaClass.greet = { -> 'hello' }

        when:
        GrailsMetaClassUtils.copyExpandoMetaClass(MetaClassSampleClass, MetaClassCopyTargetClass, false)
        MetaClassCopyTargetClass target = new MetaClassCopyTargetClass()

        then:
        target.greet() == 'hello'

        cleanup:
        GroovySystem.metaClassRegistry.removeMetaClass(MetaClassSampleClass)
        GroovySystem.metaClassRegistry.removeMetaClass(MetaClassCopyTargetClass)
    }

    void 'getPropertyIfExists returns the value when the property exists and matches the required type'() {
        given:
        def obj = new MySampleClass(name: 'John Doe')

        expect:
        GrailsMetaClassUtils.getPropertyIfExists(obj, 'name') == 'John Doe'
        GrailsMetaClassUtils.getPropertyIfExists(obj, 'name', String) == 'John Doe'
        GrailsMetaClassUtils.getPropertyIfExists(obj, 'name', Integer) == null
        GrailsMetaClassUtils.getPropertyIfExists(obj, 'noSuchProperty') == null
    }

    void 'invokeMethodIfExists invokes the method when it exists and returns null otherwise'() {
        given:
        def obj = new MySampleClass(name: 'John Doe')

        expect:
        GrailsMetaClassUtils.invokeMethodIfExists(obj, 'toString') == obj.toString()
        GrailsMetaClassUtils.invokeMethodIfExists(obj, 'noSuchMethod') == null
    }

}

class MySampleClass {
    String name
}

class MetaClassSampleClass {
}

class MetaClassCopyTargetClass {
}
