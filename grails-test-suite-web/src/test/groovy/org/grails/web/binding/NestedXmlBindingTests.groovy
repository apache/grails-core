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
package org.grails.web.binding

import grails.persistence.Entity
import grails.testing.gorm.DataTest
import grails.testing.web.controllers.ControllerUnitTest
import grails.web.Controller
import spock.lang.Specification

class NestedXmlBindingTests extends Specification implements ControllerUnitTest<NestedXmlController>, DataTest {

    Class<?>[] getDomainClassesToMock() {
        [Person, Location, Foo, Bar]
    }

    void testNestedXmlBinding() {
        when:
        request.method = 'POST'
        request.xml = '''
<person>
<name>John Doe</name>
<location>
<shippingAddress>foo</shippingAddress>
<billingAddress>bar</billingAddress>
</location>
</person>
'''
        def result = controller.bind()
        Person p = result.person

        then:
        result != null
        p != null
        p.name == "John Doe"
        p.location != null
        p.location.shippingAddress == 'foo'
        p.location.billingAddress == 'bar'
    }

    void testNestedXmlBindingWithId() {
        when:
        request.method = 'POST'
        request.xml = '''
<person>
<name>John Doe</name>
<location id="1">
<shippingAddress>foo</shippingAddress>
<billingAddress>bar</billingAddress>
</location>
</person>
'''
        def result = controller.bind()
        Person p = result.person

        then:
        result != null
        p != null
        p.name == "John Doe"
        p.location != null
        p.location.id == 1
        p.location.shippingAddress == 'foo'
        p.location.billingAddress == 'bar'
    }

    void testBindToArrayOfDomains() {
        when:
        request.method = 'POST'
        request.xml = '''
<person>
   <name>John Doe</name>
   <locations>
      <location>
         <shippingAddress>foo</shippingAddress>
         <billingAddress>bar</billingAddress>
      </location>
      <location>
         <shippingAddress>foo2</shippingAddress>
         <billingAddress>bar2</billingAddress>
      </location>
   </locations>
</person>
'''
        def result = controller.bind()
        Person p = result.person

        then:
        result != null
        p != null
        p.name == "John Doe"
        p.locations.size() == 2
        p.locations[0].shippingAddress == 'foo'
        p.locations[0].billingAddress == 'bar'
        p.locations[1].shippingAddress == 'foo2'
        p.locations[1].billingAddress == 'bar2'
    }

    void testBindToOne() {
        when:
        request.method = 'POST'
        request.xml = '''<?xml version="1.0" encoding="UTF-8"?>
<foo>
<bar id="1" />
</foo>
'''
        new Bar().save(flush:true)
        def result = controller.bindToOne()

        then:
        result != null
        result.bar != null
        result.bar.id == 1
    }

    void testBindToArrayOfDomainsWithJson() {
        when:
        request.method = 'POST'
        request.json = '''
{
"name": "John Doe",
"locations": [
{ "shipppingAddress": "foo", "billingAddress": "bar" },
{ "shipppingAddress": "foo2", "billingAddress": "bar2" }
]
}
'''
        def result = controller.bind()
        Person p = result.person

        then:
        result != null
        p != null
        p.name == "John Doe"
        p.locations.size() == 2
    }
}

@Controller
class NestedXmlController {
    def bind() {
        def person = new Person()
        person.properties = request

        [person: person]
    }

    def bindToOne() {
        def fooInstance = new Foo()
        fooInstance.properties = request
        return  fooInstance
    }
}

@Entity
class Person {
    String name
    Location location
    List<Location> locations = []
    static hasMany = [locations:Location]
}

@Entity
class Location {
    String shippingAddress
    String billingAddress

    static constraints = {
        id bindable: true
    }
}

@Entity
class Foo {
    static belongsTo = [bar: Bar]
}

@Entity
class Bar {}
