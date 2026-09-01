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
package grails.converters

import java.nio.file.Files

import groovy.xml.XmlSlurper

import org.springframework.validation.BeanPropertyBindingResult

import grails.core.DefaultGrailsApplication
import grails.web.mime.MimeType
import grails.rest.render.errors.VndErrorXmlRenderer
import grails.rest.render.hal.HalXmlCollectionRenderer
import grails.rest.render.hal.HalXmlRenderer
import org.grails.web.converters.configuration.ConvertersConfigurationHolder
import org.grails.web.converters.configuration.XmlConvertersConfigurationInitializer
import org.grails.web.converters.marshaller.xml.ValidationErrorsMarshaller
import org.grails.web.databinding.bindingsource.XmlDataBindingSourceCreator

import spock.lang.Specification

class XmlCompatibilitySpec extends Specification {

    void 'legacy XML validation error formats are deprecated for a future major release'() {
        expect:
        VndErrorXmlRenderer.getAnnotation(Deprecated).since() == '8.0'
        !VndErrorXmlRenderer.getAnnotation(Deprecated).forRemoval()
        ValidationErrorsMarshaller.getAnnotation(Deprecated).since() == '8.0'
        !ValidationErrorsMarshaller.getAnnotation(Deprecated).forRemoval()
    }

    void 'HAL XML renderers are deprecated for a future major release'() {
        expect:
        HalXmlRenderer.getAnnotation(Deprecated).since() == '8.0'
        !HalXmlRenderer.getAnnotation(Deprecated).forRemoval()
        HalXmlCollectionRenderer.getAnnotation(Deprecated).since() == '8.0'
        !HalXmlCollectionRenderer.getAnnotation(Deprecated).forRemoval()
    }

    void setup() {
        new XmlConvertersConfigurationInitializer().tap {
            grailsApplication = new DefaultGrailsApplication()
            initialize()
        }
    }

    void cleanup() {
        ConvertersConfigurationHolder.clear()
    }

    void 'maps retain their entry names and nested collection shape'() {
        given:
        XML converter = new XML([
                title: 'Grails & Spring',
                editions: [7, 8],
                metadata: [active: true]
        ])

        when:
        def document = new XmlSlurper(false, false).parseText(converter.toString())

        then:
        document.name() == 'map'
        document.entry.find { it.@key == 'title' }.text() == 'Grails & Spring'
        document.entry.find { it.@key == 'editions' }.children()*.text() == ['7', '8']
        document.entry.find { it.@key == 'metadata' }.entry.find { it.@key == 'active' }.text() == 'true'
    }

    void 'registered marshallers retain priority and custom element output'() {
        given:
        XML.registerObjectMarshaller(XmlCompatibilityValue, 100) { XmlCompatibilityValue value, XML xml ->
            xml.attribute('code', value.code)
            xml.build {
                label(value.label)
            }
        }

        when:
        def document = new XmlSlurper(false, false).parseText(
                new XML(new XmlCompatibilityValue(code: 'g8', label: 'Grails 8')).toString()
        )

        then:
        document.name() == 'xmlCompatibilityValue'
        document.@code == 'g8'
        document.label.text() == 'Grails 8'
    }

    void 'named configurations and per-write projections retain their scope'() {
        given:
        XML.createNamedConfig('compact') { configuration ->
            configuration.registerObjectMarshaller(XmlCompatibilityValue, 100) { value, xml ->
                xml.build { label(value.label) }
            }
        }
        def defaultXml = new XML(new XmlCompatibilityValue(code: 'g8', label: 'Default'))
        defaultXml.includes = ['code']

        when:
        def projected = new XmlSlurper(false, false).parseText(defaultXml.toString())
        def named
        XML.use('compact') {
            named = new XmlSlurper(false, false).parseText(
                    new XML(new XmlCompatibilityValue(code: 'g8', label: 'Named')).toString())
        }

        then:
        projected.code.text() == 'g8'
        projected.label.isEmpty()
        named.label.text() == 'Named'
        named.code.isEmpty()
    }

    void 'circular maps use a self-reference attribute without recursing'() {
        given:
        def circular = [:]
        circular.self = circular

        when:
        def document = new XmlSlurper(false, false).parseText(new XML(circular).toString())

        then:
        document.entry.find { it.@key == 'self' }.@ref == '.'
    }

    void 'external entities are not resolved by converter or binding parsers'() {
        given:
        def secret = Files.createTempFile('grails-xml-xxe-', '.txt')
        Files.writeString(secret, 'must-not-be-read')
        def xml = "<!DOCTYPE root [<!ENTITY xxe SYSTEM '${secret.toUri()}'>]><root><value>&xxe;</value></root>"

        when:
        def converted = XML.parse(xml)
        def bound = new XmlDataBindingSourceCreator().createDataBindingSource(
                MimeType.XML, Object, new StringReader(xml))

        then:
        converted.value.text() != 'must-not-be-read'
        bound['value'].toString() != 'must-not-be-read'

        cleanup:
        Files.deleteIfExists(secret)
    }

    void 'internal DTD entities remain supported without loading external resources'() {
        given:
        def xml = '<!DOCTYPE root [<!ENTITY value "internal">]><root><value>&value;</value></root>'

        expect:
        XML.parse(xml).value.text() == 'internal'
    }

    void 'validation errors retain their legacy XML element and attribute shape'() {
        given:
        XML.registerObjectMarshaller(new ValidationErrorsMarshaller(), 100)
        def errors = new BeanPropertyBindingResult(new XmlCompatibilityValue(), 'value')
        errors.rejectValue('label', 'blank', 'Label is required')

        when:
        def document = new XmlSlurper(false, false).parseText(new XML(errors).toString())

        then:
        document.name() == 'errors'
        document.error.@object == 'value'
        document.error.@field == 'label'
        document.error.'rejected-value'.text() == ''
        document.error.message.text() == 'Label is required'
    }
}

class XmlCompatibilityValue {
    String code
    String label
}
