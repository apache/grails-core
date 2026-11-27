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
package org.grails.plugins.databinding

import tools.jackson.core.json.JsonReadFeature
import tools.jackson.databind.json.JsonMapper

import grails.web.mime.MimeType
import org.grails.testing.GrailsUnitTest
import org.grails.web.databinding.bindingsource.DataBindingSourceRegistry
import org.grails.web.databinding.bindingsource.DefaultDataBindingSourceRegistry
import org.grails.web.databinding.bindingsource.InvalidRequestBodyException
import org.grails.web.databinding.bindingsource.JsonDataBindingSourceCreator
import spock.lang.Specification

/**
 * The JSON bodies of a type no creator bean handles, where an application replaces jsonDataBindingSourceCreator with a
 * creator for one class, are parsed as grails.databinding.json.jackson says.
 */
class JacksonDefaultJsonCreatorSpec extends Specification implements GrailsUnitTest {

    Closure doWithConfig() {
        { config -> config['grails.databinding.json.jackson'] = true }
    }

    Closure doWithSpring() {
        { -> jsonDataBindingSourceCreator(OneClassJsonCreator) }
    }

    void 'with Jackson parsing, they are parsed with Jackson, whose default mapper rejects a trailing comma'() {
        given: 'the replacement creator is in effect'
        assert applicationContext.getBean('jsonDataBindingSourceCreator') instanceof OneClassJsonCreator

        when:
        applicationContext.getBean(DataBindingSourceRegistry)
                .createDataBindingSource(MimeType.JSON, OtherBinding, new StringReader('{"name":"Grails",}'))

        then:
        thrown(InvalidRequestBodyException)
    }
}

/**
 * The registry the plugin defines is given its JSON default by the plugin itself, so that it does not depend on Spring
 * injecting the registry's own setting, which code generated ahead of time for the interface the bean is declared as
 * does not see.
 */
class JacksonPluginDefaultJsonCreatorSpec extends Specification implements GrailsUnitTest {

    Closure doWithConfig() {
        { config -> config['grails.databinding.json.jackson'] = true }
    }

    void 'with Jackson parsing, the plugin sets the default JSON creator of its registry to one that parses with Jackson'() {
        when:
        def creator = ((DefaultDataBindingSourceRegistry) applicationContext.getBean(DataBindingSourceRegistry))
                .defaultJsonDataBindingSourceCreator

        then:
        creator instanceof JsonDataBindingSourceCreator
        ((JsonDataBindingSourceCreator) creator).useJackson
    }
}

class JacksonApplicationMapperJsonCreatorSpec extends Specification implements GrailsUnitTest {

    Closure doWithConfig() {
        { config -> config['grails.databinding.json.jackson'] = true }
    }

    Closure doWithSpring() {
        { ->
            jsonDataBindingSourceCreator(OneClassJsonCreator)
            applicationJsonMapper(JsonMapper, JsonMapper.builder().enable(JsonReadFeature.ALLOW_JAVA_COMMENTS))
        }
    }

    void "with Jackson parsing, they are parsed by the application's mapper, with its read features"() {
        given: 'the replacement creator is in effect'
        assert applicationContext.getBean('jsonDataBindingSourceCreator') instanceof OneClassJsonCreator

        expect: 'the mapper allows a comment, which neither a default JsonMapper nor JsonSlurper accepts'
        applicationContext.getBean(DataBindingSourceRegistry)
                .createDataBindingSource(MimeType.JSON, OtherBinding, new StringReader('/* a comment */ {"name":"Grails"}'))
                .getPropertyValue('name') == 'Grails'
    }
}

class JacksonApplicationRegistryJsonCreatorSpec extends Specification implements GrailsUnitTest {

    Closure doWithConfig() {
        { config -> config['grails.databinding.json.jackson'] = true }
    }

    Closure doWithSpring() {
        { ->
            dataBindingSourceRegistry(ApplicationDataBindingSourceRegistry)
            jsonDataBindingSourceCreator(OneClassJsonCreator)
            applicationJsonMapper(JsonMapper, JsonMapper.builder().enable(JsonReadFeature.ALLOW_JAVA_COMMENTS))
        }
    }

    void "with Jackson parsing, a registry the application defines parses them with the application's mapper too"() {
        given: "the application's registry is in effect"
        def registry = applicationContext.getBean(DataBindingSourceRegistry)
        assert registry instanceof ApplicationDataBindingSourceRegistry

        expect:
        registry.createDataBindingSource(MimeType.JSON, OtherBinding, new StringReader('/* a comment */ {"name":"Grails"}'))
                .getPropertyValue('name') == 'Grails'
    }
}

class LegacyDefaultJsonCreatorSpec extends Specification implements GrailsUnitTest {

    Closure doWithSpring() {
        { -> jsonDataBindingSourceCreator(OneClassJsonCreator) }
    }

    void 'by default they are parsed by a default JsonSlurper, as in Grails 8, which accepts a trailing comma'() {
        given: 'the replacement creator is in effect'
        assert applicationContext.getBean('jsonDataBindingSourceCreator') instanceof OneClassJsonCreator

        expect:
        applicationContext.getBean(DataBindingSourceRegistry)
                .createDataBindingSource(MimeType.JSON, OtherBinding, new StringReader('{"name":"Grails",}'))
                .getPropertyValue('name') == 'Grails'
    }
}

class ApplicationDataBindingSourceRegistry extends DefaultDataBindingSourceRegistry {
}

class OneClassJsonCreator extends JsonDataBindingSourceCreator {

    @Override
    Class getTargetType() {
        OneClassBinding
    }
}

class OneClassBinding {
    String name
}

class OtherBinding {
    String name
}
