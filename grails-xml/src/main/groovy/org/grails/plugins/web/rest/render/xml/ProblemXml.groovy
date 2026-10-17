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
package org.grails.plugins.web.rest.render.xml

import javax.xml.stream.XMLOutputFactory
import javax.xml.stream.XMLStreamWriter

import groovy.transform.CompileStatic
import groovy.transform.PackageScope

import tools.jackson.databind.ObjectMapper

import org.springframework.http.ProblemDetail

/**
 * Writes a problem in the XML format of RFC 9457, as Spring's Jackson XML converter writes it, for an application without
 * {@code jackson-dataformat-xml}: a {@code problem} element in the {@code urn:ietf:rfc:7807} namespace, with the members
 * of the problem, those of a subclass included, and its extension members, as a mapper with Spring's mix-in for problems
 * converts them. A list is written as an element for each of its values, and a {@code null} as an {@code xsi:nil}
 * element.
 *
 * @since 9.0
 */
@PackageScope
@CompileStatic
final class ProblemXml {

    static final String NAMESPACE = 'urn:ietf:rfc:7807'

    private static final String XSI = 'http://www.w3.org/2001/XMLSchema-instance'

    /** The members of RFC 9457. */
    private static final Set<String> MEMBERS = ['type', 'title', 'status', 'detail', 'instance'] as Set<String>

    // The JDK's own writer, whatever StAX implementation another library puts on the classpath
    private static final XMLOutputFactory OUTPUT = XMLOutputFactory.newDefaultFactory()

    private ProblemXml() {
    }

    /**
     * @param problem the problem
     * @param writer the response writer
     * @param mapper a mapper with Spring's {@code ProblemDetailJacksonMixin}, which converts the problem as Spring's
     *        converters write it
     */
    static void write(ProblemDetail problem, Writer writer, ObjectMapper mapper) {
        XMLStreamWriter xml = OUTPUT.createXMLStreamWriter(writer)
        xml.writeStartElement('problem')
        xml.writeDefaultNamespace(NAMESPACE)
        Set<String> extensions = problem.properties?.keySet() ?: Collections.<String> emptySet()
        mapper.convertValue(problem, Map).each { Object name, Object value ->
            String member = name.toString()
            // Spring's converter writes the members of RFC 9457 and the extension members in its namespace, and the
            // properties of a subclass in none
            element(xml, member, value, MEMBERS.contains(member) || extensions.contains(member) ? null : '')
        }
        xml.writeEndElement()
        xml.flush()
    }

    /**
     * @param namespace the namespace of the element, which it declares, or {@code null} for that of the element it is in
     */
    private static void element(XMLStreamWriter xml, String name, Object value, String namespace) {
        if (value instanceof List) {
            for (Object listed : (List) value) {
                element(xml, name, listed, namespace)
            }
        }
        else if (value == null) {
            xml.writeEmptyElement(name)
            declare(xml, namespace)
            xml.writeNamespace('xsi', XSI)
            xml.writeAttribute('xsi', XSI, 'nil', 'true')
        }
        else {
            xml.writeStartElement(name)
            declare(xml, namespace)
            if (value instanceof Map) {
                ((Map<?, ?>) value).each { Object key, Object member -> element(xml, key.toString(), member, null) }
            }
            else {
                xml.writeCharacters(value.toString())
            }
            xml.writeEndElement()
        }
    }

    private static void declare(XMLStreamWriter xml, String namespace) {
        if (namespace != null) {
            xml.writeDefaultNamespace(namespace)
        }
    }
}
