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
package grails.web.databinding

import grails.databinding.SimpleMapDataBindingSource;
import grails.databinding.events.DataBindingListener;
import grails.databinding.events.DataBindingListenerAdapter;
import grails.web.databinding.GrailsWebDataBinder;
import spock.lang.Specification

class GrailsWebDataBinderListenerSpec extends Specification {

    void 'Test supports() method is respected'() {
        given:
        def personListener = new PersonDataBindingListener()
        def binder = new GrailsWebDataBinder()
        binder.setDataBindingListeners([personListener] as DataBindingListener[])
        
        when:
        def country = new Country()
        binder.bind country, [name: 'Canada'] as SimpleMapDataBindingSource
        
        then:
        country.name == 'Canada'
        personListener.bindingObjects == []
        personListener.beforeBindingData == []

        when:
        def person = new Person()
        binder.bind person, [firstName: 'Ian', lastName: 'Kilmister'] as SimpleMapDataBindingSource
        
        then:
        person.firstName == 'Ian'
        person.lastName == 'Kilmister'
        personListener.bindingObjects == [person]
        personListener.beforeBindingData == [[firstName: 'Ian'], [lastName: 'Kilmister']]
    }

}

class Person {
    String firstName
    String lastName
}

class Country {
    String name
}

class PersonDataBindingListener extends DataBindingListenerAdapter {
    
    List<?> bindingObjects = []
    List<Map<String, Object>> beforeBindingData = []
    
    @Override
    boolean supports(Class<?> c) {
        Person.isAssignableFrom c
    }
    
    @Override
    Boolean beforeBinding(Object object, Object errors) {
        bindingObjects << object
    }

    @Override
    Boolean beforeBinding(Object object, String propertyName, Object propertyValue, Object errors) {
        beforeBindingData << [(propertyName): propertyValue]    
        true
    }
}
