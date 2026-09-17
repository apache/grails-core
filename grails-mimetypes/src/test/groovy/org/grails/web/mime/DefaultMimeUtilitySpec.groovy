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
package org.grails.web.mime

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.TypeHint
import org.springframework.aot.hint.TypeReference
import spock.lang.Specification

import grails.web.mime.MimeType
import grails.web.mime.MimeUtility
import org.apache.grails.mimetypes.aot.MimeTypeRuntimeHints

class DefaultMimeUtilitySpec extends Specification {

    MimeType textXml = new MimeType('text/xml', 'xml')
    MimeType appXml = new MimeType('application/xml', 'xml')
    MimeType json = new MimeType('application/json', 'json')

    void 'the first mime type registered for an extension wins'() {
        given:
        DefaultMimeUtility fromList = new DefaultMimeUtility([textXml, appXml, json])
        DefaultMimeUtility fromArray = new DefaultMimeUtility([appXml, textXml] as MimeType[])

        expect:
        fromList instanceof MimeUtility
        fromList.knownMimeTypes == [textXml, appXml, json]
        fromList.getMimeTypeForExtension('xml').is(textXml)
        fromList.getMimeTypeForExtension('json').is(json)
        fromList.getMimeTypeForExtension('missing') == null
        fromList.getMimeTypeForExtension(null) == null
        fromArray.knownMimeTypes == [appXml, textXml]
        fromArray.getMimeTypeForExtension('xml').is(appXml)
    }

    void 'the uri extension is resolved from the last dot'() {
        given:
        DefaultMimeUtility utility = new DefaultMimeUtility([textXml, json])

        expect:
        utility.getMimeTypeForURI('/a/b.c/file.json').is(json)
        utility.getMimeTypeForURI('file.xml').is(textXml)
        utility.getMimeTypeForURI('/no/extension') == null
        utility.getMimeTypeForURI('trailing.') == null
        utility.getMimeTypeForURI('.json').is(json)
        utility.getMimeTypeForURI(null) == null
    }

    void 'the runtime hints register the content negotiation types that are present'() {
        given:
        RuntimeHints hints = new RuntimeHints()

        when:
        new MimeTypeRuntimeHints().registerHints(hints, getClass().classLoader)
        List<TypeHint> typeHints = hints.reflection().typeHints().toList()

        then:
        typeHints*.type*.name.sort() == ['grails.web.mime.MimeType', 'org.grails.web.mime.DefaultAcceptHeaderParser', 'org.grails.web.mime.DefaultMimeUtility']
        typeHints.every { TypeHint hint ->
            hint.memberCategories == [MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.INVOKE_PUBLIC_METHODS, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS] as Set
        }
        hints.reflection().getTypeHint(TypeReference.of('grails.web.mime.MimeType')) != null
    }

}
