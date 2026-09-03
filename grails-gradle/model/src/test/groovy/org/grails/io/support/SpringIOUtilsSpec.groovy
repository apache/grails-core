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

import org.xml.sax.SAXParseException
import spock.lang.Specification

class SpringIOUtilsSpec extends Specification {

    void 'createXmlSlurper parses documents without a doctype'() {
        when:
        def xml = SpringIOUtils.createXmlSlurper().parseText('<root><child>ok</child></root>')

        then:
        xml.child.text() == 'ok'
    }

    void 'createXmlSlurper rejects doctype declarations with external entities'() {
        when:
        SpringIOUtils.createXmlSlurper().parseText('''<!DOCTYPE root [
<!ENTITY ext SYSTEM "file:///not-resolved">
]>
<root>&ext;</root>''')

        then:
        thrown(SAXParseException)
    }

    void 'createXmlSlurper rejects doctype declarations with internal entities'() {
        when:
        SpringIOUtils.createXmlSlurper().parseText('''<!DOCTYPE root [
<!ENTITY msg "safe">
]>
<root>&msg;</root>''')

        then:
        thrown(SAXParseException)
    }
}
