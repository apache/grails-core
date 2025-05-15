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

import grails.databinding.DataBindingSource
import groovy.xml.XmlSlurper
import spock.lang.PendingFeatureIf
import spock.lang.Specification

class GPathCollectionDataBindingSourceSpec extends Specification {

    @PendingFeatureIf({
        // groovy.lang.MissingFieldException: No such field: id for class: groovy.xml.slurpersupport.NodeChild
        GroovySystem.version.startsWith('5')
    })
    void 'Test multiple child elements'() {
        given:
        def xml = new XmlSlurper().parseText('''
<list>
    <person>
        <firstName>Peter</firstName>
        <lastName>Gabriel</lastName>
    </person>
    <person>
        <firstName>Tony</firstName>
        <lastName>Banks</lastName>
    </person>
    <person>
        <firstName>Steve</firstName>
        <lastName>Hackett</lastName>
    </person>
</list>
''')
        when:
        def source = new GPathResultCollectionDataBindingSource(xml)
        def dataBindingSources = source.dataBindingSources

        then:
        dataBindingSources.size() == 3
        dataBindingSources[0] instanceof DataBindingSource
        dataBindingSources[0]['firstName'] == 'Peter'
        dataBindingSources[0]['lastName'] == 'Gabriel'
        dataBindingSources[1] instanceof DataBindingSource
        dataBindingSources[1]['firstName'] == 'Tony'
        dataBindingSources[1]['lastName'] == 'Banks'
        dataBindingSources[2] instanceof DataBindingSource
        dataBindingSources[2]['firstName'] == 'Steve'
        dataBindingSources[2]['lastName'] == 'Hackett'
    }
}
