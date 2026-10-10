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

import jakarta.servlet.http.HttpServletRequest
import org.springframework.mock.web.MockHttpServletRequest
import org.xml.sax.SAXParseException
import spock.lang.Specification
import spock.lang.Unroll

import org.grails.web.converters.exceptions.ConverterException

/**
 * Holds {@link XML#parse} to the parsing guarantee the user guide and the threat model state for it:
 * a document that declares a {@code DOCTYPE} is refused rather than parsed, whether or not the
 * declaration references anything external.
 *
 * <p>Refusing the declaration is what closes the entity vectors at this entry point, since an entity
 * cannot be declared without one. The parser features themselves are covered by
 * {@code SpringIOUtilsSpec}; what is pinned here is that the class the documentation names uses the
 * strict parser, through every overload an application reaches, {@code request.XML} included.
 */
class XMLParseSpec extends Specification {

    private static final String DOCUMENT = '<book><title>Grails</title></book>'

    private static final String DOCUMENT_WITH_DOCTYPE = "<!DOCTYPE book>\n${DOCUMENT}"

    @Unroll
    void 'parsing #entryPoint refuses a declared doctype'() {
        when: 'a document declaring a doctype is parsed'
            invoke(DOCUMENT_WITH_DOCTYPE)

        then: 'it is refused rather than parsed'
            ConverterException e = thrown()
            e.message == 'Error parsing XML'
            e.cause instanceof SAXParseException
            e.cause.message.contains('DOCTYPE is disallowed')

        and: 'the same document is read once the declaration is removed'
            invoke(DOCUMENT).title.text() == 'Grails'

        where:
            entryPoint     | invoke
            'a string'     | { String xml -> XML.parse(xml) }
            'a stream'     | { String xml -> XML.parse(new ByteArrayInputStream(xml.getBytes('UTF-8')), 'UTF-8') }
            'a request'    | { String xml -> XML.parse(post(xml)) }
    }

    private static HttpServletRequest post(String xml) {
        new MockHttpServletRequest('POST', '/books').tap {
            characterEncoding = 'UTF-8'
            content = xml.getBytes('UTF-8')
        }
    }
}
