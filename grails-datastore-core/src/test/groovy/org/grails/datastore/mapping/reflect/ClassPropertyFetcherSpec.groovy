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

import java.beans.PropertyDescriptor

import spock.lang.Specification

class ClassPropertyFetcherSpec extends Specification {

    void "forClass exposes the java class and its instance meta properties"() {
        when:
        ClassPropertyFetcher cpf = ClassPropertyFetcher.forClass(Sample)

        then:
        cpf.javaClass == Sample
        cpf.metaProperties*.name.sort() == ['count', 'derived', 'name', 'tags']
        cpf.reference instanceof Sample
        cpf.propertyDescriptors*.name.contains('name')
    }

    void "static property values are read through the fetcher and the static helpers"() {
        given:
        ClassPropertyFetcher cpf = ClassPropertyFetcher.forClass(Sample)

        expect:
        cpf.getPropertyValue('label') == 'sample'
        cpf.getPropertyValue('name') == null
        cpf.getPropertyValue('missing') == null
        cpf.getStaticPropertyValue('label', String) == 'sample'
        cpf.getStaticPropertyValue('label', Integer) == null
        cpf.getStaticPropertyValue('label', Object) == 'sample'
        cpf.getPropertyValue('label', String) == 'sample'
        ClassPropertyFetcher.getStaticPropertyValue(Sample, 'label', String) == 'sample'
        ClassPropertyFetcher.getStaticPropertyValue(Sample, 'label', Integer) == null
        ClassPropertyFetcher.getStaticPropertyValuesFromInheritanceHierarchy(Sample, 'label', String) == ['sample']
        cpf.getStaticPropertyValuesFromInheritanceHierarchy('missing', String) == []
    }

    void "instance property values are read through fields, getters or reflection"() {
        given:
        ClassPropertyFetcher cpf = ClassPropertyFetcher.forClass(Sample)
        Sample sample = new Sample(name: 'n', count: 2, tags: ['t'])

        expect:
        cpf.getPropertyValue(sample, 'name') == 'n'
        cpf.getPropertyValue(sample, 'count') == 2
        cpf.getPropertyValue(sample, 'label') == null
        cpf.getPropertyValue(sample, 'missing') == null
        ClassPropertyFetcher.getInstancePropertyValue(sample, 'tags') == ['t']
        ClassPropertyFetcher.getInstancePropertyValue(sample, 'derived') == 'N'
    }

    void "property types and descriptors resolve through the meta class"() {
        given:
        ClassPropertyFetcher cpf = ClassPropertyFetcher.forClass(Sample)

        expect:
        cpf.isReadableProperty('name')
        !cpf.isReadableProperty('missing')
        cpf.getPropertyType('name') == String
        cpf.getPropertyType('label') == String
        cpf.getPropertyType('label', true) == null
        cpf.getPropertyType('missing') == null
        ClassPropertyFetcher.getPropertyType(Sample, 'count') == Integer
        ClassPropertyFetcher.getPropertyType(Sample, 'missing') == null
        cpf.getDeclaredField('name').name == 'name'
        cpf.getDeclaredField('derived') == null
        cpf.getDeclaredField('missing') == null
    }

    void "property descriptors are created for readable and writable meta properties"() {
        given:
        ClassPropertyFetcher cpf = ClassPropertyFetcher.forClass(Sample)

        when:
        PropertyDescriptor name = cpf.getPropertyDescriptor('name')
        PropertyDescriptor derived = cpf.getPropertyDescriptor('derived')

        then:
        name.readMethod.name == 'getName'
        name.writeMethod.name == 'setName'
        derived.readMethod.name == 'getDerived'
        derived.writeMethod == null
        cpf.getPropertyDescriptor('label') == null
        cpf.getPropertyDescriptor('missing') == null
        ClassPropertyFetcher.createPropertyDescriptor(Sample, Sample.metaClass.getMetaProperty('label')) == null
    }

    void "properties can be selected by exact, assignable-to and assignable-from types"() {
        given:
        ClassPropertyFetcher cpf = ClassPropertyFetcher.forClass(Sample)

        expect:
        cpf.getPropertiesOfType(String)*.name.sort() == ['derived', 'name']
        cpf.getPropertiesOfType(List)*.name == ['tags']
        cpf.getPropertiesAssignableToType(CharSequence)*.name.sort() == ['derived', 'name']
        cpf.getPropertiesAssignableToType(Number)*.name == ['count']
        cpf.getPropertiesAssignableFromType(ArrayList)*.name == ['tags']
        cpf.getPropertiesAssignableFromType(Sample).empty
    }

    void "clearCache is a retained no-op"() {
        when:
        ClassPropertyFetcher.clearCache()

        then:
        noExceptionThrown()
    }

    static class Sample {
        static String label = 'sample'
        String name
        Integer count
        List<String> tags

        String getDerived() {
            name?.toUpperCase()
        }
    }
}
