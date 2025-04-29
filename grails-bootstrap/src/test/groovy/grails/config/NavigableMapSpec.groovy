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
package grails.config

import org.grails.config.NavigableMap
import spock.lang.Specification
import spock.lang.Unroll

class NavigableMapSpec extends Specification {

    @Unroll
    def "merge navigable map for #input "(Map input) {

        when:
        Map output = new NavigableMap()
        output.merge(input)

        then:
        output['js'] == 'text/javascript'
        output['json'] == ['application/json', 'text/json']
        output['xml'] == ['application/hal+xml', 'text/xml', 'application/xml']
        output['grails.cors.mappings[/api/**]'] == 'default'

        where:
        input << [
                [js: 'text/javascript', json: ['application/json', 'text/json'], xml: ['application/hal+xml', 'text/xml', 'application/xml'], 'grails.cors.mappings[/api/**]': 'default'],
                [js: 'text/javascript', 'json[0]': 'application/json', 'json[1]': 'text/json', 'xml[0]': 'application/hal+xml', 'xml[1]':'text/xml', 'xml[2]':'application/xml', 'grails.cors.mappings[/api/**]': 'default'],
                ]
    }

    def "multiple subscript entries are collapse to a list of maps"() {
        given:
        Map input = [
                'rabbitmq.connections[0].name': 'main',
                'rabbitmq.connections[0].host': '1109201498',
                'rabbitmq.connections[0].host2': '635494740',
                'rabbitmq.connections[0].username': 'guest',
                'rabbitmq.connections[0].password': 'guest',
        ]

        when:
        Map output = new NavigableMap()
        output.merge(input)

        then:
        output.keySet() as List<String> == ['rabbitmq.connections']
        output['rabbitmq.connections'] instanceof List
        output['rabbitmq.connections'].size() == 1
        output['rabbitmq.connections'][0].name == 'main'
        output['rabbitmq.connections'][0].host == '1109201498'
        output['rabbitmq.connections'][0].host2 == '635494740'
        output['rabbitmq.connections'][0].username == 'guest'
        output['rabbitmq.connections'][0].password == 'guest'
    }

    def "multiple subscript entries are collapse to a map of maps"() {
        given:
        Map input = [
                'rabbitmq.connections[foo].name': 'main',
                'rabbitmq.connections[foo].host': '1109201498',
                'rabbitmq.connections[foo].host2': '635494740',
                'rabbitmq.connections[foo].username': 'guest',
                'rabbitmq.connections[foo].password': 'guest',
        ]

        when:
        Map output = new NavigableMap()
        output.merge(input)

        then:
        output.keySet() as List<String> == ['rabbitmq.connections']
        output['rabbitmq.connections'] instanceof Map
        output['rabbitmq.connections'].size() == 1
        output['rabbitmq.connections']['foo'].name == 'main'
        output['rabbitmq.connections']['foo'].host == '1109201498'
        output['rabbitmq.connections']['foo'].host2 == '635494740'
        output['rabbitmq.connections']['foo'].username == 'guest'
        output['rabbitmq.connections']['foo'].password == 'guest'
    }
}
