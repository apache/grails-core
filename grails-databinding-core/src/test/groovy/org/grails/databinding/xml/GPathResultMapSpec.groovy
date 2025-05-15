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
package org.grails.databinding.xml

import groovy.xml.XmlSlurper
import spock.lang.PendingFeatureIf
import spock.lang.Specification

class GPathResultMapSpec extends Specification {

    @PendingFeatureIf({
        // groovy.lang.MissingFieldException: No such field: id for class: groovy.xml.slurpersupport.NodeChild
        GroovySystem.version.startsWith('5')
    })
    void 'Test nested elements'() {
        given:
        def xml = new XmlSlurper().parseText('''
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
''')
        when:
        def person = new GPathResultMap(xml)

        then:
        person.size() == 2
        person.name == 'John Doe'
        person.locations instanceof Map
        person.locations.size() == 1
        person.locations.entrySet().size() == 1
        person.locations.location instanceof List
        person.locations.location.size() == 2
        person.locations.location[0] instanceof Map
        person.locations.location[0].shippingAddress == 'foo'
        person.locations.location[0].billingAddress == 'bar'
        person.locations.location[1] instanceof Map
        person.locations.location[1].shippingAddress == 'foo2'
        person.locations.location[1].billingAddress == 'bar2'
    }

    @PendingFeatureIf({
        // groovy.lang.MissingFieldException: No such field: id for class: groovy.xml.slurpersupport.NodeChild
        GroovySystem.version.startsWith('5')
    })
    void 'Test basic Map operations'() {
        given:
        def xml = new XmlSlurper().parseText('''
<sports>
    <country>USA</country>
    <baseball>
        <team>Cardinals</team>
        <team>Cubs</team>
        <equipment>
            <bats>
                <material>wood</material>
                <manufacturer>Easton</manufacturer>
                <manufacturer>Louisville Slugger</manufacturer>
            </bats>
        </equipment>
    </baseball>
    <hockey>
        <team>Blues</team>
        <team>Blackhawks</team>
    </hockey>
</sports>
''')
        when:
        def sports = new GPathResultMap(xml)

        then:
        sports.size() == 3
        sports.country == 'USA'
        sports.baseball instanceof Map
        sports.baseball.team instanceof List
        sports.baseball.team.size() == 2
        sports.baseball.team[0] == 'Cardinals'
        sports.baseball.team[1] == 'Cubs'
        sports.hockey instanceof Map
        sports.hockey.team instanceof List
        sports.hockey.team.size() == 2
        sports.hockey.team[0] == 'Blues'
        sports.hockey.team[1] == 'Blackhawks'
        sports.baseball.equipment.bats instanceof Map
        sports.baseball.equipment.bats.size() == 2
        sports.baseball.equipment.bats.material == 'wood'
        sports.baseball.equipment.bats.manufacturer instanceof List
        sports.baseball.equipment.bats.manufacturer.size() == 2
        sports.baseball.equipment.bats.manufacturer[0] == 'Easton'
        sports.baseball.equipment.bats.manufacturer[1] == 'Louisville Slugger'
        sports.containsKey 'country'
        !sports.containsKey('foo')
        !sports.isEmpty()

        when:
        def entries = sports.entrySet()

        then:
        entries.size() == 3

        when:
        def keys = sports.keySet()

        then:
        keys.size() == 3
        'hockey' in keys
        'baseball' in keys
        'country' in keys
    }

    @PendingFeatureIf({
        // groovy.lang.MissingFieldException: No such field: id for class: groovy.xml.slurpersupport.NodeChild
        GroovySystem.version.startsWith('5')
    })
    void 'Test id element'() {
        given:
        def xml = new XmlSlurper().parseText('''
<post>
    <id>42</id>
    <name>Thin Lizzy</name>
</post>
''')

        when:
        def map = new GPathResultMap(xml)

        then:
        map.size() == 2
        map.keySet().size() == 2
        map.containsKey('id')
        map.containsKey('name')
        map.id == '42'
        map.name == 'Thin Lizzy'
    }

    @PendingFeatureIf({
        // groovy.lang.MissingFieldException: No such field: id for class: groovy.xml.slurpersupport.NodeChild
        GroovySystem.version.startsWith('5')
    })
    void 'Test id returns null when no id is present'() {
        given:
        def xml = new XmlSlurper().parseText('''
<music>
    <band>
        <name>Genesis</name>
    </band>
</music>
''')

        when:
        def map = new GPathResultMap(xml)

        then:
        map.band.id == null
    }

    @PendingFeatureIf({
        // groovy.lang.MissingFieldException: No such field: id for class: groovy.xml.slurpersupport.NodeChild
        GroovySystem.version.startsWith('5')
    })
    void 'Test id attributes'() {
        given:
        def xml = new XmlSlurper().parseText('''
<music>
    <band id="4">
        <name>Thin Lizzy</name>
        <members>
            <member id="1">
                <name>Phil</name>
            </member>
            <member id="2">
                <name>Scott</name>
            </member>
            <member>
                <name>John</name>
            </member>
        </members>
    </band>
</music>
''')

        when:
        def map = new GPathResultMap(xml)

        then:
        map.band.id == '4'
        map.band.name == 'Thin Lizzy'

        when:
        def members = map.band.members

        then:
        members instanceof Map
        members.size() == 1
        members.member instanceof List
        members.member.size() == 3
        members.member[0].containsKey 'id'
        members.member[0].id == '1'
        members.member[0].name == 'Phil'
        members.member[1].containsKey 'id'
        members.member[1].id == '2'
        members.member[1].name == 'Scott'
        !members.member[2].containsKey('id')
        members.member[2].name == 'John'

        when:
        def keys = members.member[0].keySet()

        then:
        keys.size() == 2
        'id' in keys
        'name' in keys

        when:
        keys = members.member[1].keySet()

        then:
        keys.size() == 2
        'id' in keys
        'name' in keys

        when:
        keys = members.member[2].keySet()

        then:
        keys.size() == 1
        'name' in keys

        when:
        xml = new XmlSlurper().parseText('''<person>
        <name>John Doe</name>
        <location id="1">
        <shippingAddress>foo</shippingAddress>
        <billingAddress>bar</billingAddress>
        </location>
        </person>''')
        map = new GPathResultMap(xml)

        then:
        map.location.id == '1'

        when:
        xml = new XmlSlurper().parseText('''<foo>
<bar id="1" />
</foo>
''')
        map = new GPathResultMap(xml)

        then:
        map.bar.id == '1'
    }

    @PendingFeatureIf({
        // groovy.lang.MissingFieldException: No such field: id for class: groovy.xml.slurpersupport.NodeChild
        GroovySystem.version.startsWith('5')
    })
    void 'Test empty Map'() {
        given:
        def xml = new XmlSlurper().parseText('''
<root>
</root>
''')
        when:
        def map = new GPathResultMap(xml)

        then:
        map.isEmpty()
        !map.containsKey('foo')
        map.size() == 0
    }
}
