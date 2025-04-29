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
package grails.plugin.json.view

import com.fasterxml.jackson.databind.ObjectMapper
import grails.persistence.Entity
import grails.views.json.test.JsonViewUnitTest
import spock.lang.Shared
import spock.lang.Specification

class EnumRenderingSpec extends Specification implements JsonViewUnitTest {

    @Shared
    ObjectMapper objectMapper

    void setupSpec() {
        objectMapper = new ObjectMapper()
    }

    void 'Test the render method when a domain instance defines an enum'() {
        when: 'rendering an object that defines an enum'
        mappingContext.addPersistentEntity(EnumTest)
        def result = render('''
            model {
                Object object
            }
            json g.render(object)
        ''', [object: new EnumTest(name: 'Fred', bar: TestEnum.BAR)])

        then: 'the json is rendered correctly'
        result.json['bar'] == 'BAR'
        result.json['name'] == 'Fred'
    }

    void 'Test the render method when a POGO instance defines an enum'() {
        when: 'rendering an object that defines an enum'
        def result = render('''
            model {
                Object object
            }
            json g.render(object)
        ''', [object: new EnumTest(name:'Fred', bar: TestEnum.BAR)])

        then: 'the json is rendered correctly'
        result.json['bar'] == 'BAR'
        result.json['name'] == 'Fred'
    }

    void 'Test the jsonapi render method when a domain instance defines an enum'() {
        when: 'rendering an object that defines an enum'
        mappingContext.addPersistentEntity(EnumTest)
        EnumTest enumTest = new EnumTest(name: 'Fred', bar: TestEnum.BAR)
        enumTest.id = 1
        def result = render('''
            model {
                Object object
            }
            json jsonapi.render(object)
        ''', [object: enumTest])

        then: 'the json is rendered correctly'
        objectMapper.readTree(result.jsonText) == objectMapper.readTree('''
            {
                "data": {
                    "type": "enumTest",
                    "id": "1",
                    "attributes": {
                        "name": "Fred",
                        "bar": "BAR"
                    }
                },
                "links": {
                    "self": "http://localhost:8080/enumTest/show/1"
                }
            }
        ''')
    }
}

@Entity
class EnumTest {
    String name
    TestEnum bar
}

enum TestEnum { FOO, BAR }