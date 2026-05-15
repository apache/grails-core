/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * 'License'); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.grails.testing.http.client.utils

import java.nio.charset.StandardCharsets
import java.nio.file.Files

import groovy.xml.XmlSlurper

import org.xml.sax.SAXParseException
import spock.lang.Specification
import spock.lang.Unroll

class XmlUtilsSpec extends Specification {

    @Unroll
    void 'toXml allows custom formatting options'() {
        given:
        def format = new XmlUtils.Format(
                doubleQuotes: doubleQ,
                expandEmptyElements: expEmptyEle,
                omitEmptyAttributes: omitEmptyAttr,
                omitNullAttributes: omitNullAttr,
                spaceInEmptyElements: spaceInEmptyEle,
                prettyPrint: false
        )

        when:
        def xml = XmlUtils.toXml(format) {
            product(a: 'a', b: '', c: null) {
                empty()
            }
        }

        then:
        xml == expectedXml

        where:
        doubleQ | expEmptyEle | omitEmptyAttr | omitNullAttr | spaceInEmptyEle || expectedXml
        true    | false       | false         | false        | true            || '<product a='a' b='' c=''><empty /></product>'
        true    | false       | false         | false        | false           || '<product a='a' b='' c=''><empty/></product>'
        true    | true        | false         | false        | false           || '<product a='a' b='' c=''><empty></empty></product>'
        false   | false       | true          | false        | false           || "<product a='a' c=''><empty/></product>"
        true    | false       | false         | true         | true            || '<product a='a' b=''><empty /></product>'
        false   | true        | true          | true         | false           || "<product a='a'><empty></empty></product>"
    }

    void 'toXml optionally prepends doctype declaration'() {
        given:
        def format = new XmlUtils.Format(doctype: '<!DOCTYPE product SYSTEM 'product.dtd'>')

        when:
        def xml = XmlUtils.toXml(format) {
            product {
                id('1')
            }
        }

        then:
        xml == '<!DOCTYPE product SYSTEM 'product.dtd'><product><id>1</id></product>'
    }

    void 'toXml can include declaration before doctype'() {
        given:
        def format = new XmlUtils.Format(
                omitDeclaration: false,
                doctype: '<!DOCTYPE product SYSTEM 'product.dtd'>'
        )

        when:
        def xml = XmlUtils.toXml(format) {
            product {
                id('1')
            }
        }

        then:
        xml.startsWith('<?xml version='1.0' encoding='UTF-8'?>')
        xml.contains('<!DOCTYPE product SYSTEM 'product.dtd'>')
        xml.endsWith('<product><id>1</id></product>')
    }

    void 'toXml uses declaration charset version and quote style from format'() {
        given:
        def format = new XmlUtils.Format(
                charset: StandardCharsets.UTF_16,
                xmlVersion: '1.1',
                doubleQuotes: false,
                omitDeclaration: false,
                prettyPrint: false
        )

        when:
        def xml = XmlUtils.toXml(format) {
            product()
        }

        then:
        xml == "<?xml version='1.1' encoding='UTF-16'?><product />"
    }

    void 'toXml uses custom line separator in prefix'() {
        given:
        def format = new XmlUtils.Format(
                omitDeclaration: false,
                prettyPrint: true,
                doctype: '<!DOCTYPE product SYSTEM 'product.dtd'>',
                lineSeparator: '|'
        )

        when:
        def xml = XmlUtils.toXml(format) {
            product {
                id('1')
            }
        }

        then:
        xml == '<?xml version='1.0' encoding='UTF-8'?>|<!DOCTYPE product SYSTEM 'product.dtd'>|<product>| <id>1</id>|</product>'
    }

    void 'toXml uses custom indentation when pretty printing'() {
        given:
        def format = new XmlUtils.Format(indent: '--', lineSeparator: '|', prettyPrint: true)

        when:
        def xml = XmlUtils.toXml(format) {
            product {
                id('1')
            }
        }

        then:
        xml == '<product>|--<id>1</id>|</product>'
    }

    @Unroll
    void 'toXml configures attribute escaping'() {
        given:
        def format = new XmlUtils.Format(escapeAttributes: escapeAttr)

        when:
        def xml = XmlUtils.toXml(format) {
            product(code: 'A&B<C')
        }

        then:
        xml == expectedXml

        where:
        escapeAttr || expectedXml
        true       || '<product code='A&amp;B&lt;C' />'
        false      || '<product code='A&B<C' />'
    }

    void 'toXml honors direct delegate formatting overrides over passed format values'() {
        given:
        def format = new XmlUtils.Format(omitNullAttributes: false)

        when:
        def xml = XmlUtils.toXml(format) {
            omitNullAttributes = true
            product(nullAttr: null, keep: 'yes') {
                name('Widget')
            }
        }

        then:
        xml == '<product keep='yes'><name>Widget</name></product>'
    }

    void 'toXml honors direct delegate escapeAttributes override over passed format value'() {
        given:
        def format = new XmlUtils.Format(escapeAttributes: true)

        when:
        def xml = XmlUtils.toXml(format) {
            escapeAttributes = false
            product(code: 'A&B<C')
        }

        then:
        xml == '<product code='A&B<C' />'
    }

    void 'toXml preserves dsl xml declaration when format omits declaration'() {
        given:
        def format = new XmlUtils.Format(omitDeclaration: true, doctype: '<!DOCTYPE product SYSTEM 'product.dtd'>')

        when:
        def xml = XmlUtils.toXml(format) {
            mkp.xmlDeclaration(version: '1.1', encoding: 'UTF-16')
            product {
                id('1')
            }
        }

        then:
        xml == '<?xml version='1.1' encoding='UTF-16'?><!DOCTYPE product SYSTEM 'product.dtd'><product><id>1</id></product>'
    }
    
    void 'toXml builds expected XML using markup DSL'() {
        when:
        def xml = XmlUtils.toXml {
            product {
                id('1')
                name('Widget')
            }
        }

        then:
        xml.contains('<product>')
        xml.contains('<id>1</id>')
        xml.contains('<name>Widget</name>')
    }

    void 'toXml supports defaults when no format is provided'() {
        when:
        def xml = XmlUtils.toXml {
            product(type: 'tool')
        }

        then:
        xml.contains('type='tool'')
        !xml.startsWith('<xml version=')
    }

    void 'toXml accepts inline named formatting options'() {
        when:
        def xml = XmlUtils.toXml(omitNullAttributes: true, spaceInEmptyElements: false) {
            product(name: 'Widget', description: null) {
                empty()
            }
        }

        then:
        xml == '<product name='Widget'><empty/></product>'
    }

    void 'toXml keeps empty and null attributes by default'() {
        when:
        def xml = XmlUtils.toXml {
            product(emptyAttr: '', nullAttr: null)
        }

        then:
        xml.contains('emptyAttr=''')
        xml.contains('nullAttr=''')
    }

    void 'toXml emits only one declaration and lets dsl declaration take precedence'() {
        given:
        def format = new XmlUtils.Format(omitDeclaration: false, doubleQuotes: false)

        when:
        def xml = XmlUtils.toXml(format) {
            mkp.xmlDeclaration(version: '1.1', encoding: 'UTF-16')
            product()
        }

        then:
        xml == "<?xml version='1.1' encoding='UTF-16'?><product />"
    }

    void 'newXmlSlurper allows inline doctype declarations with internal entities'() {
        when:
        def parsed = XmlUtils.newXmlSlurper().parseText('''<!DOCTYPE root [
<!ENTITY msg 'safe'>
]>
<root>&msg;</root>''')

        then:
        parsed.text() == 'safe'
    }

    void 'newXmlSlurper blocks external entities'() {
        given:
        def secret = 'xml-utils-secret'
        def secretFile = Files.createTempFile('xml-utils-secret', '.txt')
        Files.writeString(secretFile, secret)
        def uri = secretFile.toUri().toASCIIString()
        def xml = """<!DOCTYPE root [
<!ENTITY ext SYSTEM '${uri}'>
]>
<root>&ext;</root>"""

        when:
        XmlUtils.newXmlSlurper().parseText(xml)

        then:
        def e = thrown(SAXParseException)
        e.message.contains('External Entity')

        cleanup:
        Files.deleteIfExists(secretFile)
    }

    void 'newXmlSlurper supports custom factory overrides'() {
        given:
        int factoryCalls = 0
        def slurper = XmlUtils.newXmlSlurper(factory: {
            factoryCalls++
            new XmlSlurper(false, false)
        })

        when:
        def parsed = slurper.parseText('<root><item>value</item></root>')

        then:
        parsed.item.text() == 'value'
        factoryCalls == 1
    }
}
