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

import org.xml.sax.SAXParseException
import org.xml.sax.helpers.DefaultHandler

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
 *
 * <p>Two parsers are handed out. The strict one, which every no-argument method returns, is for
 * untrusted input and refuses a {@code DOCTYPE}. The tolerant one, requested with {@code true}, is
 * for trusted descriptors that declare one; it is the parser that can be driven past the
 * declaration, so it is the one the entity and DTD assertions run against.
 */
class SpringIOUtilsSpec extends Specification {

    /** Shape of a JSP 1.2 tag library descriptor, as shipped inside jakarta jstl. */
    private static final String TLD = '''<!DOCTYPE taglib
  PUBLIC "-//Sun Microsystems, Inc.//DTD JSP Tag Library 1.2//EN"
  "http://java.sun.com/dtd/web-jsptaglibrary_1_2.dtd">
<taglib>
  <uri>jakarta.tags.core</uri>
  <tag><name>out</name><tag-class>org.example.OutTag</tag-class></tag>
</taglib>'''

    private static final String SECRET = 'top-secret-token'

    @TempDir
    Path tempDir

    @TempDir
    File tempFileDir

    private String externalEntityDocument() {
        Path secret = tempDir.resolve('secret.txt')
        Files.writeString(secret, SECRET)
        """<!DOCTYPE root [
<!ENTITY ext SYSTEM '${secret.toUri().toASCIIString()}'>
]>
<root>&ext;</root>"""
    }

    private static String parseWithSaxParser(javax.xml.parsers.SAXParser parser, String xml) {
        StringBuilder text = new StringBuilder()
        parser.parse(new ByteArrayInputStream(xml.getBytes('UTF-8')), new DefaultHandler() {
            @Override
            void characters(char[] chars, int start, int length) {
                text.append(chars, start, length)
            }
        })
        text.toString()
    }

    void 'createXmlSlurper parses a document without a doctype'() {
        when:
        def xml = SpringIOUtils.createXmlSlurper().parseText('<root><child>ok</child></root>')

        then:
        xml.child.text() == 'ok'
    }

    void 'createXmlSlurper rejects a doctype declaration by default'() {
        when:
        SpringIOUtils.createXmlSlurper().parseText(TLD)

        then:
        SAXParseException e = thrown()
        e.message.contains('DOCTYPE is disallowed')
    }

    void 'createXmlSlurper rejects an internal doctype subset by default'() {
        when:
        SpringIOUtils.createXmlSlurper().parseText('''<!DOCTYPE root [
<!ENTITY msg "safe">
]>
<root>&msg;</root>''')

        then:
        SAXParseException e = thrown()
        e.message.contains('DOCTYPE is disallowed')
    }

    void 'declining doctype tolerance explicitly is the default'() {
        when:
        SpringIOUtils.createXmlSlurper(false).parseText(TLD)

        then:
        SAXParseException e = thrown()
        e.message.contains('DOCTYPE is disallowed')
    }

    void 'newSAXParser rejects a doctype declaration by default'() {
        when:
        parseWithSaxParser(SpringIOUtils.newSAXParser(), TLD)

        then:
        SAXParseException e = thrown()
        e.message.contains('DOCTYPE is disallowed')
    }

    void 'newSAXParser returns a namespace-aware, non-validating parser'() {
        when:
        def parser = SpringIOUtils.newSAXParser()

        then:
        parser.isNamespaceAware()
        !parser.isValidating()
    }

    void 'asking for doctype tolerance parses a descriptor that declares one'() {
        when:
        def parsed = SpringIOUtils.createXmlSlurper(true).parseText(TLD)

        then:
        parsed.uri.text() == 'jakarta.tags.core'
        parsed.tag.name.text() == 'out'
    }

    void 'the doctype-tolerant slurper does not resolve external general entities'() {
        given: 'a document whose entity points at a readable file on disk'
        String xml = externalEntityDocument()

        when:
        def parsed = SpringIOUtils.createXmlSlurper(true).parseText(xml)

        then: 'tolerating the declaration does not reopen the XXE vector'
        !parsed.text().contains(SECRET)
    }

    void 'the doctype-tolerant slurper does not resolve external parameter entities'() {
        given: 'a parameter entity that would pull a file into the internal subset'
        Path secret = tempDir.resolve('secret.dtd')
        Files.writeString(secret, "<!ENTITY leaked '${SECRET}'>")
        String xml = """<!DOCTYPE root [
<!ENTITY % ext SYSTEM '${secret.toUri().toASCIIString()}'>
%ext;
]>
<root>ok</root>"""

        when:
        def parsed = SpringIOUtils.createXmlSlurper(true).parseText(xml)

        then:
        parsed.text() == 'ok'
    }

    void 'the doctype-tolerant slurper skips an external dtd rather than retrieving it'() {
        given: 'a document naming a DTD that does not exist, so retrieval would fail loudly'
        String xml = """<!DOCTYPE root SYSTEM '${tempDir.resolve('missing.dtd').toUri().toASCIIString()}'>
<root>ok</root>"""

        expect:
        SpringIOUtils.createXmlSlurper(true).parseText(xml).text() == 'ok'
    }

    void 'the doctype-tolerant sax parser applies the same entity hardening'() {
        given:
        String xml = externalEntityDocument()

        when:
        String text = parseWithSaxParser(SpringIOUtils.newSAXParser(true), xml)

        then:
        !text.contains(SECRET)
    }

    void 'both parsers are namespace aware'() {
        given:
        String xml = '<t:root xmlns:t="urn:test"><t:child>ok</t:child></t:root>'

        expect:
        SpringIOUtils.createXmlSlurper(allowDocType).parseText(xml).child.text() == 'ok'

        where:
        allowDocType << [false, true]
    }

    void 'byteArrayToHexString converts bytes to lowercase hex'() {
        expect:
        SpringIOUtils.byteArrayToHexString([0x00, 0x0f, (byte) 0xff] as byte[]) == '000fff'
    }

    void 'byteArrayToHexString returns null for a null or empty array'() {
        expect:
        SpringIOUtils.byteArrayToHexString(null) == null
        SpringIOUtils.byteArrayToHexString(new byte[0]) == null
    }

    void 'copy(byte[], File) writes the bytes to the file'() {
        given:
        File out = new File(tempFileDir, 'out.bin')

        when:
        SpringIOUtils.copy('hello'.bytes, out)

        then:
        out.text == 'hello'
    }

    void 'copy(File, File) copies file contents'() {
        given:
        File source = new File(tempFileDir, 'source.txt')
        source.text = 'copy me'
        File target = new File(tempFileDir, 'target.txt')

        when:
        int count = SpringIOUtils.copy(source, target)

        then:
        target.text == 'copy me'
        count == 'copy me'.bytes.length
    }

    void 'copyToByteArray(File) reads the whole file'() {
        given:
        File source = new File(tempFileDir, 'source.txt')
        source.text = 'bytes here'

        expect:
        new String(SpringIOUtils.copyToByteArray(source)) == 'bytes here'
    }

    void 'copyToString(Reader) reads the whole reader'() {
        expect:
        SpringIOUtils.copyToString(new StringReader('reader contents')) == 'reader contents'
    }

    void 'copy(String, Writer) writes the string and closes the writer'() {
        given:
        StringWriter writer = new StringWriter()

        when:
        SpringIOUtils.copy('written text', writer)

        then:
        writer.toString() == 'written text'
    }

    void 'computeChecksum computes an md5 digest'() {
        given:
        File source = new File(tempFileDir, 'checksum.txt')
        source.text = 'checksum me'

        expect:
        SpringIOUtils.computeChecksum(source, 'md5').length() == 32
    }

    void 'computeChecksum rejects an unknown algorithm'() {
        given:
        File source = new File(tempFileDir, 'checksum.txt')
        source.text = 'checksum me'

        when:
        SpringIOUtils.computeChecksum(source, 'not-an-algorithm')

        then:
        thrown(IllegalArgumentException)
    }

    void 'closeQuietly closes without throwing'() {
        given:
        Closeable closeable = { -> } as Closeable

        expect:
        SpringIOUtils.closeQuietly(closeable)
        SpringIOUtils.closeQuietly(null)
    }

    void 'closeQuietly swallows an IOException from close'() {
        given:
        Closeable closeable = { -> throw new IOException('boom') } as Closeable

        when:
        SpringIOUtils.closeQuietly(closeable)

        then:
        noExceptionThrown()
    }

    void 'addAll concatenates two arrays preserving order'() {
        expect:
        SpringIOUtils.addAll(['a', 'b'] as String[], ['c', 'd'] as String[]) == ['a', 'b', 'c', 'd']
    }

}
