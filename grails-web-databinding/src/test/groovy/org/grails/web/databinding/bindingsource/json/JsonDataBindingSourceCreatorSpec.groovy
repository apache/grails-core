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

import groovy.json.JsonParserType
import groovy.json.JsonSlurper

import org.grails.core.lifecycle.ShutdownOperations
import org.grails.web.databinding.bindingsource.HalJsonDataBindingSourceCreator
import org.grails.web.databinding.bindingsource.JsonApiDataBindingSourceCreator
import org.grails.web.databinding.bindingsource.JsonDataBindingSourceCreator
import grails.web.databinding.GrailsWebDataBinder
import grails.web.mime.MimeType

import spock.lang.Specification

import java.nio.charset.StandardCharsets

import org.grails.web.databinding.bindingsource.InvalidRequestBodyException

import tools.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.annotation.JsonTypeInfo
import tools.jackson.databind.DefaultTyping
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import tools.jackson.databind.ObjectReader
import tools.jackson.databind.json.JsonMapper
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer
import org.springframework.core.env.MapPropertySource
import org.springframework.http.HttpInputMessage
import org.springframework.http.HttpOutputMessage
import org.springframework.http.MediaType
import org.springframework.http.converter.AbstractHttpMessageConverter
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter

class JsonDataBindingSourceCreatorSpec extends Specification {

    void 'JSON decimals bind as BigDecimal so fractional values keep their digits, with #parser'() {
        given: "a body carrying values that lose digits when read as a double"
        def json = '{"price": 1.15, "precise": 12345678901234567890.12345, "qty": 3}'
        def inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))

        when:
        def bindingSource = creator.createDataBindingSource(MimeType.JSON, Object, inputStream)

        then: "decimals arrive as BigDecimal, matching the digits the request sent"
        bindingSource['price'] instanceof BigDecimal
        bindingSource['price'] == 1.15G
        bindingSource['precise'] instanceof BigDecimal
        bindingSource['precise'].toPlainString() == '12345678901234567890.12345'

        and: "integral values are unaffected"
        bindingSource['qty'] instanceof Integer
        bindingSource['qty'] == 3

        where:
        parser              | creator
        'the legacy parser' | new JsonDataBindingSourceCreator()
        'Jackson'           | new JsonDataBindingSourceCreator(jsonMapper: JsonMapper.builder().build(), useJackson: true)
    }

    void 'with Jackson, #number binds as the legacy parser binds it'() {
        given:
        def body = { new ByteArrayInputStream("{\"n\": ${number}}".getBytes(StandardCharsets.UTF_8)) }
        def jackson = new JsonDataBindingSourceCreator(jsonMapper: JsonMapper.builder().build(), useJackson: true)

        when:
        def parsed = jackson.createDataBindingSource(MimeType.JSON, Object, body())['n']
        def legacy = new JsonDataBindingSourceCreator().createDataBindingSource(MimeType.JSON, Object, body())['n']

        then:
        parsed.getClass() == legacy.getClass()
        parsed == legacy

        where:
        number << ['3', '-7', '2147483648', '9223372036854775807', '9223372036854775808', '1.15', '1.0', '1e3', '-0',
                   '12345678901234567890.12345']
    }

    void 'JSON decimals inside a collection body also bind as BigDecimal, with #parser'() {
        given:
        def inputStream = new ByteArrayInputStream('[{"price": 1.15}]'.getBytes(StandardCharsets.UTF_8))

        when:
        def sources = creator.createCollectionDataBindingSource(MimeType.JSON, Object, inputStream)

        then:
        sources.dataBindingSources.first()['price'] instanceof BigDecimal
        sources.dataBindingSources.first()['price'] == 1.15G

        where:
        parser              | creator
        'the legacy parser' | new JsonDataBindingSourceCreator()
        'Jackson'           | new JsonDataBindingSourceCreator(jsonMapper: JsonMapper.builder().build(), useJackson: true)
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

    void 'uses the JsonMapper it is given for request parsing'() {
        given:
        def jsonMapper = JsonMapper.builder()
                .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
                .build()
        def creator = new JsonDataBindingSourceCreator(jsonMapper: jsonMapper, useJackson: true)
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
        def source = new JsonDataBindingSourceCreator(useJackson: true).createDataBindingSource(
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

    void 'Grails 9 accepts legacy syntax by default and Jackson is an explicit opt in'() {
        expect:
        new JsonDataBindingSourceCreator().createDataBindingSource(MimeType.JSON, Object, new StringReader(body))['a'] != null

        when:
        new JsonDataBindingSourceCreator(useJackson: true)
                .createDataBindingSource(MimeType.JSON, Object, new StringReader(body))

        then:
        thrown(InvalidRequestBodyException)

        where:
        body << ['{"a":1} trailing', '{"a":1,}', '{"a":01}', '{"a":"\t"}', '{"a":1}//c']
    }

    void 'the legacy parsing notice is logged once however many JSON binding sources start'() {
        given: 'a new application'
        ShutdownOperations.runOperations()
        def context = new AnnotationConfigApplicationContext()
        context.registerBean(PropertySourcesPlaceholderConfigurer)
        context.registerBean(JsonDataBindingSourceCreator)
        context.registerBean(HalJsonDataBindingSourceCreator)
        context.registerBean(JsonApiDataBindingSourceCreator)

        when:
        String output = stderrWhile { context.refresh() }

        then:
        output.readLines().count { it.contains(LEGACY_PARSING_NOTICE) } == 1
        output.contains('Set grails.databinding.json.jackson=true to opt into Jackson; it becomes the default in Grails 10.')

        cleanup:
        context.close()
        ShutdownOperations.runOperations()
    }

    void 'a subclass of a JSON binding source creator whose own initialize method takes no arguments is not initialized by it'() {
        given:
        ShutdownOperations.runOperations()
        def context = new AnnotationConfigApplicationContext()
        context.registerBean(PropertySourcesPlaceholderConfigurer)
        context.registerBean(InitializingJsonCreator)

        when:
        context.refresh()

        then: 'Spring calls only what the subclass marks to be called'
        !context.getBean(InitializingJsonCreator).initialized

        cleanup:
        context.close()
        ShutdownOperations.runOperations()
    }

    void 'an application context logs the legacy parsing notice as it starts the creator bean, unless #description'() {
        given: 'a new application'
        ShutdownOperations.runOperations()
        def context = new AnnotationConfigApplicationContext()
        context.environment.propertySources.addFirst(new MapPropertySource('test', settings))
        context.registerBean(PropertySourcesPlaceholderConfigurer)
        context.registerBean(JsonDataBindingSourceCreator)

        when:
        String output = stderrWhile { context.refresh() }

        then:
        output.contains(LEGACY_PARSING_NOTICE) == logged

        cleanup:
        context.close()
        ShutdownOperations.runOperations()

        where:
        description                 | settings                                  || logged
        'it is left to its default' | [:]                                       || true
        'it opts into Jackson'      | ['grails.databinding.json.jackson': true] || false
    }

    void "Jackson request parsing reads a body as it is where the application's mapper unwraps root values, with #creator.class.simpleName"() {
        given: 'a mapper that unwraps the root value of a type it reads, as spring.jackson.deserialization.unwrap-root-value sets'
        creator.jsonMapper = JsonMapper.builder().enable(DeserializationFeature.UNWRAP_ROOT_VALUE).build()

        expect: 'the body has no root value of the type the creator reads it as'
        creator.createDataBindingSource(MimeType.JSON, Object, new StringReader('{"name":"Grails"}'))['name'] == 'Grails'
        creator.createCollectionDataBindingSource(MimeType.JSON, Object, new StringReader('[{"name":"Grails"}]'))
                .dataBindingSources*.getPropertyValue('name') == ['Grails']

        where:
        creator << [new JsonDataBindingSourceCreator(useJackson: true), new HalJsonDataBindingSourceCreator(useJackson: true)]
    }

    void "Jackson request parsing reads lists and whole numbers as the legacy parser does where the application's mapper enables #feature"() {
        given:
        def creator = new JsonDataBindingSourceCreator(useJackson: true)
        creator.jsonMapper = JsonMapper.builder().enable(feature).build()

        when:
        def source = creator.createDataBindingSource(MimeType.JSON, Object, new StringReader('{"tags":["a","b"],"count":3}'))

        then: 'a list and an Integer'
        source.getPropertyValue('tags') instanceof List
        source.getPropertyValue('tags') == ['a', 'b']
        source.getPropertyValue('count').getClass() == Integer

        where:
        feature << [DeserializationFeature.USE_JAVA_ARRAY_FOR_JSON_ARRAY, DeserializationFeature.USE_BIG_INTEGER_FOR_INTS,
                    DeserializationFeature.USE_LONG_FOR_INTS]
    }

    void "Jackson request parsing reads a body without type ids where the application's mapper reads them by #description, with #creator.class.simpleName"() {
        given:
        creator.jsonMapper = mapper

        when:
        def source = creator.createDataBindingSource(MimeType.JSON, Object, new StringReader('{"name":"Grails","tags":["a"],"count":3}'))
        def sources = creator.createCollectionDataBindingSource(MimeType.JSON, Object, new StringReader('[{"name":"Grails"}]'))

        then:
        source.getPropertyValue('name') == 'Grails'
        source.getPropertyValue('tags') == ['a']
        source.getPropertyValue('count').getClass() == Integer
        sources.dataBindingSources*.getPropertyValue('name') == ['Grails']

        where:
        [description, mapper, creator] << [
                [['default typing for Object', defaultTyping(DefaultTyping.JAVA_LANG_OBJECT)],
                 ['default typing for every type that is not final', defaultTyping(DefaultTyping.NON_FINAL)],
                 ['a mix-in that names the class of every Object', JsonMapper.builder().addMixIn(Object, ClassTypeIds).build()]],
                [new JsonDataBindingSourceCreator(useJackson: true), new HalJsonDataBindingSourceCreator(useJackson: true)]
        ].combinations().collect { List row -> [row[0][0], row[0][1], row[1]] }
    }

    void "a body read with Jackson cannot name a class for Grails to create, where the application's mapper reads type ids by #description"() {
        given:
        def creator = new JsonDataBindingSourceCreator(useJackson: true)
        creator.jsonMapper = mapper

        when:
        def source = creator.createDataBindingSource(MimeType.JSON, Object,
                new StringReader('{"@class":"java.util.LinkedHashMap","created":["java.util.Date",0]}'))

        then: 'the values as the body writes them'
        source.getPropertyValue('@class') == 'java.util.LinkedHashMap'
        source.getPropertyValue('created') == ['java.util.Date', 0]

        where:
        description                                    | mapper
        'default typing for every type that is not final' | defaultTyping(DefaultTyping.NON_FINAL)
        'a mix-in that names the class of every Object' | JsonMapper.builder().addMixIn(Object, ClassTypeIds).build()
    }

    void 'a mapper set after a body was read is the one the next body is read with'() {
        given: 'a mapper with default typing that allows comments, and a body read with it'
        def creator = new JsonDataBindingSourceCreator(useJackson: true)
        creator.jsonMapper = JsonMapper.builder().enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
                .activateDefaultTyping(BasicPolymorphicTypeValidator.builder().allowIfSubType(Object).build(), DefaultTyping.NON_FINAL)
                .build()
        assert creator.createDataBindingSource(MimeType.JSON, Object, new StringReader('/* c */ {"a":1}'))['a'] == 1

        when: 'a default mapper is set, which allows none'
        creator.jsonMapper = JsonMapper.builder().build()
        creator.createDataBindingSource(MimeType.JSON, Object, new StringReader('/* c */ {"a":1}'))

        then:
        thrown(InvalidRequestBodyException)
    }

    void "a body is read by the application's own mapper, of its own class, where it has no default typing"() {
        given:
        def mapper = new ReaderCountingMapper()
        def creator = new JsonDataBindingSourceCreator(useJackson: true)
        creator.jsonMapper = mapper

        when:
        creator.createDataBindingSource(MimeType.JSON, Object, new StringReader('{"a":1}'))

        then:
        mapper.readers > 0
    }

    private static JsonMapper defaultTyping(DefaultTyping typing) {
        JsonMapper.builder()
                .activateDefaultTyping(BasicPolymorphicTypeValidator.builder().allowIfSubType(Object).build(), typing,
                        JsonTypeInfo.As.PROPERTY)
                .build()
    }

    void 'the JsonSlurper of the legacy path is deprecated for removal in Grails 11'() {
        expect:
        JsonDataBindingSourceCreator.getDeclaredField('jsonSlurper').getAnnotation(Deprecated).forRemoval()
        JsonDataBindingSourceCreator.getDeclaredField('jsonSlurper').getAnnotation(Deprecated).since() == '9.0'
    }

    void 'an application JsonSlurper bean parses bodies on the legacy path, as on Grails 8'() {
        given: 'a lax parser, which accepts unquoted keys'
        def context = new AnnotationConfigApplicationContext()
        context.registerBean(PropertySourcesPlaceholderConfigurer)
        context.registerBean(JsonSlurper) { new JsonSlurper().setType(JsonParserType.LAX) }
        context.registerBean(JsonDataBindingSourceCreator)
        context.refresh()

        expect:
        context.getBean(JsonDataBindingSourceCreator)
                .createDataBindingSource(MimeType.JSON, Object, new StringReader('{name: Grails}'))['name'] == 'Grails'

        cleanup:
        context.close()
    }

    void 'Jackson request parsing uses the application\'s only JsonMapper, whatever its name'() {
        given:
        def context = new AnnotationConfigApplicationContext()
        context.environment.propertySources.addFirst(new MapPropertySource('test', ['grails.databinding.json.jackson': true]))
        context.registerBean(PropertySourcesPlaceholderConfigurer)
        context.registerBean('applicationMapper', JsonMapper) {
            JsonMapper.builder().enable(JsonReadFeature.ALLOW_JAVA_COMMENTS).build()
        }
        context.registerBean(JsonDataBindingSourceCreator)
        context.refresh()

        expect:
        context.getBean(JsonDataBindingSourceCreator).createDataBindingSource(MimeType.JSON, Object,
                new StringReader('{/* comment */"name":"Grails"}'))['name'] == 'Grails'

        cleanup:
        context.close()
    }

    void 'Jackson request parsing uses the conventional Boot mapper when two non primary mappers exist'() {
        given:
        def context = new AnnotationConfigApplicationContext()
        context.environment.propertySources.addFirst(new MapPropertySource('test', ['grails.databinding.json.jackson': true]))
        context.registerBean(PropertySourcesPlaceholderConfigurer)
        context.registerBean('jacksonJsonMapper', JsonMapper) {
            JsonMapper.builder().enable(JsonReadFeature.ALLOW_JAVA_COMMENTS).build()
        }
        context.registerBean('otherMapper', JsonMapper) { JsonMapper.builder().build() }
        context.registerBean(JsonDataBindingSourceCreator)
        context.refresh()

        expect:
        context.getBean(JsonDataBindingSourceCreator).createDataBindingSource(MimeType.JSON, Object,
                new StringReader('{/* comment */"name":"Grails"}'))['name'] == 'Grails'

        cleanup:
        context.close()
    }

    void "Jackson request parsing uses the mapper Spring MVC reads a request body with, ahead of the application's #description"() {
        given: 'a converter that reads comments, which the application mapper does not'
        def context = new AnnotationConfigApplicationContext()
        context.environment.propertySources.addFirst(new MapPropertySource('test', ['grails.databinding.json.jackson': true]))
        context.registerBean(PropertySourcesPlaceholderConfigurer)
        context.registerBean('jacksonJsonMapper', JsonMapper) { JsonMapper.builder().build() }
        context.registerBean('requestMappingHandlerAdapter', RequestMappingHandlerAdapter) {
            new RequestMappingHandlerAdapter().tap {
                messageConverters = [converter()] as List<HttpMessageConverter<?>>
            }
        }
        context.registerBean(JsonDataBindingSourceCreator)
        context.refresh()

        expect:
        context.getBean(JsonDataBindingSourceCreator).createDataBindingSource(MimeType.JSON, Object,
                new StringReader('{/* comment */"name":"Grails"}'))['name'] == 'Grails'

        cleanup:
        context.close()

        where:
        description                  | converter
        'its own mapper'             | { -> new JacksonJsonHttpMessageConverter(JsonMapper.builder().enable(JsonReadFeature.ALLOW_JAVA_COMMENTS).build()) }
        'the one it has for maps'    | { ->
            new JacksonJsonHttpMessageConverter(JsonMapper.builder().build()).tap {
                registerMappersForType(Map) { Map<MediaType, JsonMapper> mappers ->
                    mappers.put(MediaType.APPLICATION_JSON, JsonMapper.builder().enable(JsonReadFeature.ALLOW_JAVA_COMMENTS).build())
                }
            }
        }
    }

    void "Jackson request parsing uses the application's mapper where Spring MVC reads JSON with a converter that is not Jackson's"() {
        given: 'a converter ahead of Jackson\'s that reads JSON maps otherwise, as Gson\'s does'
        def context = new AnnotationConfigApplicationContext()
        context.environment.propertySources.addFirst(new MapPropertySource('test', ['grails.databinding.json.jackson': true]))
        context.registerBean(PropertySourcesPlaceholderConfigurer)
        context.registerBean('jacksonJsonMapper', JsonMapper) {
            JsonMapper.builder().enable(JsonReadFeature.ALLOW_JAVA_COMMENTS).build()
        }
        context.registerBean('requestMappingHandlerAdapter', RequestMappingHandlerAdapter) {
            new RequestMappingHandlerAdapter().tap {
                messageConverters = [new OtherJsonConverter(), new JacksonJsonHttpMessageConverter(JsonMapper.builder().build())] as List<HttpMessageConverter<?>>
            }
        }
        context.registerBean(JsonDataBindingSourceCreator)
        context.refresh()

        expect:
        context.getBean(JsonDataBindingSourceCreator).createDataBindingSource(MimeType.JSON, Object,
                new StringReader('{/* comment */"name":"Grails"}'))['name'] == 'Grails'

        cleanup:
        context.close()
    }

    private static final String LEGACY_PARSING_NOTICE = 'Legacy JSON request parsing is deprecated for removal in Grails 11.'

    private static String stderrWhile(Closure<?> work) {
        PrintStream original = System.err
        def buffer = new ByteArrayOutputStream()
        System.setErr(new PrintStream(buffer, true))
        try {
            work.call()
        }
        finally {
            System.setErr(original)
        }
        buffer.toString()
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

class InitializingJsonCreator extends JsonDataBindingSourceCreator {

    boolean initialized

    void initialize() {
        initialized = true
    }
}

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
abstract class ClassTypeIds {
}

class ReaderCountingMapper extends JsonMapper {

    int readers

    ReaderCountingMapper() {
        super(JsonMapper.builder())
    }

    @Override
    ObjectReader reader() {
        readers++
        super.reader()
    }
}

/** A converter that reads JSON maps without Jackson, as Gson's does. */
class OtherJsonConverter extends AbstractHttpMessageConverter<Map> {

    OtherJsonConverter() {
        super(MediaType.APPLICATION_JSON)
    }

    @Override
    protected boolean supports(Class<?> type) {
        Map.isAssignableFrom(type)
    }

    @Override
    protected Map readInternal(Class<? extends Map> type, HttpInputMessage message) {
        [:]
    }

    @Override
    protected void writeInternal(Map map, HttpOutputMessage message) {
    }
}
