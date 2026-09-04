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
package org.grails.io.support

import java.nio.file.Files
import java.nio.file.Path

import spock.lang.Specification
import spock.lang.TempDir

/**
 * Asserts the parser hardening applied by {@link SpringIOUtils} through observable parsing
 * behaviour rather than by reading feature flags back off the factory.
 *
 * <p>This is deliberate. Reading the flags back would require this spec to hold its own copy of
 * the feature identifiers, so a search-and-replace over those identifiers would rewrite the
 * production code and this spec together and the suite would still pass. Driving real documents
 * through the parser keeps the assertions independent of how the hardening is spelled.
 */
class SpringIOUtilsSpec extends Specification {

    @TempDir
    Path tempDir

    void 'createXmlSlurper parses a document without a doctype'() {
        when:
        def xml = SpringIOUtils.createXmlSlurper().parseText('<root><child>ok</child></root>')

        then:
        xml.child.text() == 'ok'
    }

    void 'createXmlSlurper does not resolve external entities'() {
        given: 'a document whose entity points at a readable file on disk'
        Path secret = tempDir.resolve('secret.txt')
        Files.writeString(secret, 'top-secret-token')
        String xml = """<!DOCTYPE root [
<!ENTITY ext SYSTEM '${secret.toUri().toASCIIString()}'>
]>
<root>&ext;</root>"""

        when: 'the document is parsed'
        def parsed = SpringIOUtils.createXmlSlurper().parseText(xml)

        then: 'the entity contributes nothing and the file contents do not leak'
        !parsed.text().contains('top-secret-token')
    }

    void 'createXmlSlurper does not retrieve an external dtd'() {
        given: 'a doctype naming a dtd that could not be read if it were fetched'
        String xml = """<!DOCTYPE root SYSTEM '${tempDir.resolve('missing.dtd').toUri().toASCIIString()}'>
<root>ok</root>"""

        expect: 'the external subset is skipped rather than fetched, so parsing succeeds'
        SpringIOUtils.createXmlSlurper().parseText(xml).text() == 'ok'
    }

    void 'createXmlSlurper parses classpath descriptors that declare a public doctype'() {
        given: 'the shape of a JSP 1.2 tag library descriptor, as shipped inside jakarta jstl'
        String tld = '''<!DOCTYPE taglib
  PUBLIC "-//Sun Microsystems, Inc.//DTD JSP Tag Library 1.2//EN"
  "http://java.sun.com/dtd/web-jsptaglibrary_1_2.dtd">
<taglib>
  <uri>jakarta.tags.core</uri>
  <tag><name>out</name><tag-class>org.example.OutTag</tag-class></tag>
</taglib>'''

        when: 'the descriptor is parsed the way TldReader parses it'
        def parsed = SpringIOUtils.createXmlSlurper().parseText(tld)

        then: 'the descriptor is readable; rejecting the doctype here breaks jsp tag resolution'
        parsed.uri.text() == 'jakarta.tags.core'
        parsed.tag.name.text() == 'out'
    }

    void 'createXmlSlurper resolves entities declared in an internal subset'() {
        when:
        def parsed = SpringIOUtils.createXmlSlurper().parseText('''<!DOCTYPE root [
<!ENTITY msg "safe">
]>
<root>&msg;</root>''')

        then:
        parsed.text() == 'safe'
    }

    void 'newSAXParser applies the same hardening as createXmlSlurper'() {
        given:
        Path secret = tempDir.resolve('secret.txt')
        Files.writeString(secret, 'top-secret-token')
        String xml = """<!DOCTYPE root [
<!ENTITY ext SYSTEM '${secret.toUri().toASCIIString()}'>
]>
<root>&ext;</root>"""
        StringBuilder text = new StringBuilder()

        when:
        SpringIOUtils.newSAXParser().parse(new ByteArrayInputStream(xml.getBytes('UTF-8')),
                new org.xml.sax.helpers.DefaultHandler() {
                    @Override
                    void characters(char[] chars, int start, int length) {
                        text.append(chars, start, length)
                    }
                })

        then:
        !text.toString().contains('top-secret-token')
    }
}
