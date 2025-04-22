/* Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.databinding

import grails.databinding.BindingFormat

import grails.databinding.SimpleDataBinder
import grails.databinding.SimpleMapDataBindingSource
import grails.databinding.events.DataBindingListenerAdapter
import spock.lang.Specification

class BindingListenerSpec extends Specification {

    void 'Test using beforeBinding to initialize property'() {
        given:
        def binder = new SimpleDataBinder()
        def p = new Package()
        def listener = new EmployeeBindingListener()

        when:
        binder.bind p, new SimpleMapDataBindingSource([recipient: [name: 'Jenny', employeeNumber: '8675309']]), listener

        then:
        p.recipient instanceof Employee
        p.recipient.name == 'Jenny'
        p.recipient.employeeNumber == '8675309'
    }

    void 'Test afterBinding'() {
        given:
        def binder = new SimpleDataBinder()
        def p = new Person()
        def listener = new PersonBindingListener()

        when:
        binder.bind(p, new SimpleMapDataBindingSource([name: 'Phil']), listener)

        then:
        listener.valuesAfterBinding.name == 'Phil'
    }
    
    void 'Test listener for properties with a converter associated with them'() {
        given:
        def binder = new SimpleDataBinder()
        def p = new Person()
        def listener = new  PersonBindingListener()
        
        when:
        binder.bind p, [birthDate: '11151969'] as SimpleMapDataBindingSource, listener
        
        then:
        listener.valuesAfterBinding.birthDate instanceof Date
        listener.valuesAfterBinding.birthDate.month == Calendar.NOVEMBER
        listener.valuesAfterBinding.birthDate.year == 69
        listener.valuesAfterBinding.birthDate.date == 15
        listener.valuesAfterBinding.birthDate == p.birthDate
        listener.valuesBeforeBinding.birthDate == p.birthDate
    }

    void 'Test that beforeBinding can return null or true to indicate that binding should proceed'() {
        given:
        def binder = new SimpleDataBinder()
        def p = new Person()
        def listener = new PersonBindingListener2()

        when:
        binder.bind p, new SimpleMapDataBindingSource([name: 'one']), listener

        then:
        p.name == 'one'

        when:
        p.name = null
        binder.bind p, new SimpleMapDataBindingSource([name: 'two']), listener

        then:
        p.name == 'two'

        when:
        p.name = null
        binder.bind p, new SimpleMapDataBindingSource([name: 'three']), listener

        then:
        p.name == null

        when:
        binder.bind p, new SimpleMapDataBindingSource([name: 'four']), listener

        then:
        p.name == null
    }
}

class PersonBindingListener2 extends DataBindingListenerAdapter {

    Boolean beforeBinding(obj, String propertyName, value, errors) {
        if (value == 'one') {
            return true
        }
        if (value == 'two') {
            return null
        }
        if(value == 'three') {
            return false
        }
        false
    }
}

class PersonBindingListener extends DataBindingListenerAdapter {

    def valuesAfterBinding = [:]
    def valuesBeforeBinding = [:]

    void afterBinding(obj, String propertyName, errors) {
        valuesAfterBinding[propertyName] = obj[propertyName]
    }
    
    public Boolean beforeBinding(Object obj, String propertyName, Object value, Object errors) {
        valuesBeforeBinding[propertyName] = value
        true
    }

}

class EmployeeBindingListener extends DataBindingListenerAdapter {

    Boolean beforeBinding(obj, String propertyName, value, errors) {
        if ('recipient' == propertyName && obj['recipient'] == null) {
            obj['recipient'] = new Employee()
            return false
        }
        return true
    }
}

class Person {
    String name
    
    @BindingFormat('MMddyyyy')
    Date birthDate
}

class Employee extends Person {
    String employeeNumber
}

class Package {
    Person recipient
}
