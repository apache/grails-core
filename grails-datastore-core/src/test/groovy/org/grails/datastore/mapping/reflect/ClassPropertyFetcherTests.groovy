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

package org.grails.datastore.mapping.reflect

import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingFactory
import org.grails.datastore.mapping.model.config.GormProperties
import org.junit.jupiter.api.Test

import java.beans.PropertyDescriptor

/**
 * @author Graeme Rocher
 * @since 1.1
 */
class ClassPropertyFetcherTests  {

    @Test
    void testGetProperty() {
        def cpf = ClassPropertyFetcher.forClass(Foo)

        assert 'foo' == cpf.getPropertyValue("name")
        assert cpf.getPropertiesAssignableToType(CharSequence).size() == 1
        assert cpf.getPropertiesAssignableToType(String).size() == 1
    }

    @Test
    void testGetStaticPropertyInheritance() {
        def tc = ClassPropertyFetcher.forClass(TransientChild)
        def tp = ClassPropertyFetcher.forClass(TransientParent)
        def tsc = ClassPropertyFetcher.forClass(TransientSubChild)

        assert tp.getStaticPropertyValuesFromInheritanceHierarchy(GormProperties.TRANSIENT, Collection) == [[]]
        assert tc.getStaticPropertyValuesFromInheritanceHierarchy(GormProperties.TRANSIENT, Collection) == [[], ["transientProperty"]]
        assert tsc.getStaticPropertyValuesFromInheritanceHierarchy(GormProperties.TRANSIENT, Collection) == [[], ["transientProperty"], ["bar"]]
    }


    @Test
    void testClassPropertyFetcherWithTraitProperty() {
        def cpf = ClassPropertyFetcher.forClass(DomainWithTrait)

        def metaProperties = cpf.getMetaProperties()

        assert DomainWithTrait.getDeclaredMethod("getFrom").returnType == DomainWithTrait
        assert metaProperties.size() == 2

        def prop = metaProperties.find { it.name == 'from' }

        assert prop != null
        assert prop.type == DomainWithTrait

        KeyValueMappingFactory mappingFactory = new KeyValueMappingFactory("test")

        PropertyDescriptor descriptor = mappingFactory.createPropertyDescriptor(DomainWithTrait, prop)
        assert descriptor != null
    }

    @Test
    void testClassPropertyFetcherWithMultipleSetter() {
        def cpf = ClassPropertyFetcher.forClass(DomainWithMultipleSetter)

        def metaProperties = cpf.getMetaProperties()

        assert metaProperties.size() == 2

        def prop = metaProperties.find { it.name == 'id' }

        assert prop != null
        assert prop.type == Long

    }

    @Test
    void testGetObjectTypeForWrappedBeanProperty() {
        GroovyObject mc = (GroovyObject)Foo.metaClass

        // Wrap the getter and setter similar to how they'd be wrapped for hibernate proxy handling
        mc.setProperty("getBar", {->
            delegate.@bar
        })
        mc.setProperty("setBar", {
            delegate.@bar = it
        })

        // The default meta property type is Object
        assert Foo.metaClass.getMetaProperty('bar').getType() == Object

        // The class property fetcher returns the real type via the Field
        def cpf = ClassPropertyFetcher.forClass(Foo)
        assert cpf.getPropertyType('bar') == String
    }

    static class Foo {
        static String name = "foo"

        String bar
    }
}

trait TestTrait<F extends Serializable> {
    F from
}

class DomainWithTrait implements Serializable, TestTrait<DomainWithTrait> {
    String name
}

class TransientParent {

    static mapWith = 'neo4j'
    static transients = []
}

class TransientChild extends TransientParent {
    String name
    String transientProperty

    String getTransientProperty() {
        return transientProperty
    }

    void setTransientProperty(String transientProperty) {
        this.transientProperty = transientProperty
    }
    static transients = ["transientProperty"]
}

class TransientSubChild extends TransientChild {

    String foo
    String bar

    static transients = ["bar"]
}

class DomainWithMultipleSetter {
    Long id
    String name

    void setId(String id) {
        this.id = Long.parseLong(id)
    }

    void setId(Long id) {
        this.id = id
    }
}
