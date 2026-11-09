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
package org.grails.web.databinding.bindingsource

import grails.web.mime.MimeType

import org.grails.databinding.bindingsource.DataBindingSourceCreationException
import org.xml.sax.SAXParseException

import spock.lang.Specification
import spock.lang.Unroll

/**
 * XML request bodies are untrusted input, so they are parsed with the strict parser: a body that
 * declares a {@code DOCTYPE} is refused before anything is bound, whether or not the declaration
 * references anything external.
 */
class XmlDataBindingSourceCreatorSpec extends Specification {

    void 'an xml request body binds its elements'() {
        given:
        def creator = new XmlDataBindingSourceCreator()

        when:
        def source = creator.createDataBindingSource(MimeType.XML, Object,
                new StringReader('<book><title>Grails</title></book>'))

        then:
        source.getPropertyValue('title') == 'Grails'
    }

    @Unroll
    void 'a request body declaring a doctype is refused by #creator.class.simpleName'() {
        when:
        creator.createDataBindingSource(creator.mimeTypes[0], Object, new StringReader('''<!DOCTYPE book [
<!ENTITY title "Grails">
]>
<book><title>&title;</title></book>'''))

        then:
        InvalidRequestBodyException e = thrown()
        e.cause instanceof SAXParseException
        e.cause.message.contains('DOCTYPE is disallowed')

        where:
        creator << [new XmlDataBindingSourceCreator(), new HalXmlDataBindingSourceCreator()]
    }

    void 'a collection request body declaring a doctype is refused'() {
        given:
        def creator = new XmlDataBindingSourceCreator()

        when:
        creator.createCollectionDataBindingSource(MimeType.XML, Object,
                new StringReader('<!DOCTYPE books><books><book><title>Grails</title></book></books>'))

        then:
        DataBindingSourceCreationException e = thrown()
        e.cause instanceof SAXParseException
        e.cause.message.contains('DOCTYPE is disallowed')
    }
}
