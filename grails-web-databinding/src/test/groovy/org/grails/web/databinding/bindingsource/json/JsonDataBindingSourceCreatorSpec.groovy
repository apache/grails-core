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
package org.grails.web.databinding.bindingsource.json

import org.grails.web.databinding.bindingsource.JsonDataBindingSourceCreator
import grails.web.databinding.GrailsWebDataBinder
import grails.web.mime.MimeType

import spock.lang.Specification

import java.nio.charset.StandardCharsets

import org.grails.web.databinding.bindingsource.InvalidRequestBodyException

import tools.jackson.core.json.JsonReadFeature
import tools.jackson.databind.json.JsonMapper

class JsonDataBindingSourceCreatorSpec extends Specification {

    void 'JSON decimals bind as BigDecimal so fractional values keep their digits'() {
        given: "a body carrying values that lose digits when read as a double"
        def json = '{"price": 1.15, "precise": 12345678901234567890.12345, "qty": 3}'
        def inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))

        when:
        def bindingSource = new JsonDataBindingSourceCreator()
                .createDataBindingSource(MimeType.JSON, Object, inputStream)

        then: "decimals arrive as BigDecimal, matching the digits the request sent"
        bindingSource['price'] instanceof BigDecimal
        bindingSource['price'] == 1.15G
        bindingSource['precise'] instanceof BigDecimal
        bindingSource['precise'].toPlainString() == '12345678901234567890.12345'

        and: "integral values are unaffected"
        bindingSource['qty'] instanceof Integer
        bindingSource['qty'] == 3
    }

    void 'JSON decimals inside a collection body also bind as BigDecimal'() {
        given:
        def inputStream = new ByteArrayInputStream('[{"price": 1.15}]'.getBytes(StandardCharsets.UTF_8))

        when:
        def sources = new JsonDataBindingSourceCreator()
                .createCollectionDataBindingSource(MimeType.JSON, Object, inputStream)

        then:
        sources.dataBindingSources.first()['price'] instanceof BigDecimal
    }

    void 'Test JSON parsing'() {
        given:
        def json = '''{
  "category": {"name":"laptop", "shouldBeNull": null, "shouldBeTrue": true, "shouldBeFalse": false, "someNumber": 42},
  "name": "MacBook",
  "languages" : [ {"name": "Groovy", "company": "GoPivotal"}, {"name": "Java", "company": "Oracle"}]
}'''

        def inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))
        def bindingSource = new JsonDataBindingSourceCreator().createDataBindingSource(MimeType.JSON, Object, inputStream)

        when:
        def propertyNames = bindingSource.propertyNames

        then:
        propertyNames.contains 'category'
        propertyNames.contains 'name'
        bindingSource.containsProperty 'category'
        bindingSource.containsProperty 'name'
        bindingSource['name'] == 'MacBook'
        bindingSource['category'] instanceof Map
//        !(bindingSource['category'] instanceof JsonObjectMap)
        bindingSource['category'].size() == 5
        bindingSource['category']['name'] == 'laptop'
        bindingSource['category']['shouldBeTrue'] == true
        bindingSource['category']['shouldBeFalse'] == false
        bindingSource['category']['someNumber'].intValue() == 42
        bindingSource['category']['shouldBeNull'] == null
        bindingSource['languages'] instanceof List
        bindingSource['languages'][0] instanceof Map
//        !(bindingSource['languages'][0] instanceof JsonObjectMap)
        bindingSource['languages'][1] instanceof Map
//        !(bindingSource['languages'][1] instanceof JsonObjectMap)
        bindingSource['languages'][0]['name'] == 'Groovy'
        bindingSource['languages'][0]['company'] == 'GoPivotal'
        bindingSource['languages'][1]['name'] == 'Java'
        bindingSource['languages'][1]['company'] == 'Oracle'
    }

    void 'Test malformed JSON parsing'() {
        given:
        def json = '''{"mapData": {"name":"Jeff{{{'''

        def inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))


        when:
        def bindingSource = new JsonDataBindingSourceCreator().createDataBindingSource(MimeType.JSON, Object, inputStream)

        then:
        thrown InvalidRequestBodyException
    }

    void 'uses an injected Boot JsonMapper for request parsing'() {
        given:
        def jsonMapper = JsonMapper.builder()
                .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
                .build()
        def creator = new JsonDataBindingSourceCreator(jsonMapper: jsonMapper)
        def inputStream = new ByteArrayInputStream('{/* configured mapper */"name":"Grails"}'.bytes)

        when:
        def bindingSource = creator.createDataBindingSource(MimeType.JSON, Object, inputStream)

        then:
        bindingSource['name'] == 'Grails'
    }

    void 'Jackson parsed sources preserve nested indexed null and missing values through public binding'() {
        given:
        def json = '''{
  "name": "updated",
  "protectedValue": "attempted",
  "missingValue": null,
  "child": {"name": "nested"},
  "children": [{"name": "zero"}, {"name": "one"}]
}'''
        def source = new JsonDataBindingSourceCreator().createDataBindingSource(
                MimeType.JSON, JsonBindingTarget, new ByteArrayInputStream(json.bytes))
        def target = new JsonBindingTarget(
                name: 'original', protectedValue: 'protected', missingValue: 'retained')

        when:
        new GrailsWebDataBinder(null).bind(
                target, source, ['name', 'missingValue', 'child', 'children'], ['protectedValue'])

        then:
        target.name == 'updated'
        target.protectedValue == 'protected'
        target.missingValue == null
        target.child.name == 'nested'
        target.children*.name == ['zero', 'one']
    }

    void 'missing JSON properties do not clear existing target values'() {
        given:
        def source = new JsonDataBindingSourceCreator().createDataBindingSource(
                MimeType.JSON, JsonBindingTarget, new StringReader('{"name":"updated"}'))
        def target = new JsonBindingTarget(name: 'original', missingValue: 'retained')

        when:
        new GrailsWebDataBinder(null).bind(target, source)

        then:
        target.name == 'updated'
        target.missingValue == 'retained'
    }

    void 'top-level JSON arrays create one binding source per object element'() {
        when:
        def sources = new JsonDataBindingSourceCreator().createCollectionDataBindingSource(
                MimeType.JSON, JsonBindingTarget, new StringReader('[{"name":"first"},42,{"name":"third"}]'))

        then:
        (sources.dataBindingSources[0].propertyNames as Set) == (['name'] as Set)
        sources.dataBindingSources[1].propertyNames.empty
        (sources.dataBindingSources[2].propertyNames as Set) == (['name'] as Set)
        sources.dataBindingSources[0]['name'] == 'first'
        sources.dataBindingSources[2]['name'] == 'third'
    }
}

class JsonBindingTarget {
    String name
    String protectedValue
    String missingValue
    JsonBindingChild child
    List<JsonBindingChild> children
}

class JsonBindingChild {
    String name
}
