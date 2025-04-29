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
package grails.test.mixin

import grails.databinding.SimpleMapDataBindingSource
import grails.databinding.converters.ValueConverter
import org.grails.spring.beans.factory.InstanceFactoryBean
import org.grails.testing.GrailsUnitTest
import spock.lang.Specification

/**
 * @author Lari Hotari
 */
class SpyBeanSpec extends Specification implements GrailsUnitTest {

    def myAddressValueConverterMock =Spy(MyAddressValueConverter)

    Closure doWithSpring() {{ ->
        myAddressValueConverter(InstanceFactoryBean, myAddressValueConverterMock, MyAddressValueConverter)
    }}

    def "it's possible to use Spy instances as beans as well"() {
        given:
        def binder=grailsApplication.mainContext.getBean("grailsWebDataBinder")
        def person=new MyPerson()
        when:
        binder.bind person, [name:'Lari', address:'Espoo,Finland'] as SimpleMapDataBindingSource
        then:
        1 * myAddressValueConverterMock.canConvert('Espoo,Finland')
        1 * myAddressValueConverterMock.convert('Espoo,Finland')
        0 * myAddressValueConverterMock._
        person.address.city=='Espoo'
        person.address.country=='Finland'
    }
}

class MyPerson {
    String name
    MyAddress address
}

class MyAddress {
    String city
    String country
}

class MyAddressValueConverter implements ValueConverter {
    boolean canConvert(value) {
        value instanceof String
    }

    def convert(value) {
        def pieces = value.split(',')
        new MyAddress(city: pieces[0], country: pieces[1])
    }

    Class<?> getTargetType() {
        MyAddress
    }
}
