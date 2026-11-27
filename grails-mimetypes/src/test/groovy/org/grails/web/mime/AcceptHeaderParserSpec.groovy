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

import grails.core.DefaultGrailsApplication
import grails.spring.BeanBuilder
import grails.util.Holders
import grails.web.mime.MimeType
import org.grails.config.PropertySourcesConfig
import org.grails.plugins.web.mime.MimeTypesConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.MutablePropertySources
import spock.lang.Specification

import static org.junit.jupiter.api.Assertions.assertEquals

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class AcceptHeaderParserSpec extends Specification {

    def config

    void setup() {
        def configObject = new ConfigSlurper()
                .parse("""
grails.mime.types = [ xml: ['text/xml', 'application/xml'],
                      text: 'text/plain',
                      js: 'text/javascript',
                      rss: 'application/rss+xml',
                      atom: 'application/atom+xml',
                      css: 'text/css',
                      cvs: 'text/csv',
                      all: '*/*',
                      json: 'application/json',
                      html: ['text/html','application/xhtml+xml'],
                      acme: 'application/vnd.Acme+json',
                      foov1: 'application/vnd.foo+json;v=1.0',
                      foov2: 'application/vnd.foo+json;v=2.0'
                    ]
        """)
        def ps = new MutablePropertySources()
        ps.addLast(new MapPropertySource("grails", configObject))
        config = new PropertySourcesConfig(ps)
    }

    void cleanup() {
        config = null
        Holders.setConfig null
    }

    void testXmlContentTypeWithCharset() {

        when:
        DefaultAcceptHeaderParser parser = getAcceptHeaderParser()
        def mimes = parser.parse("text/xml; charset=UTF-8")

        then:
        1 == mimes.size()
        "application/xml" == mimes[0].name
        "xml" == mimes[0].extension
        'UTF-8' == mimes[0].parameters.charset
        '1.0' == mimes[0].parameters.q
        '1.0' == mimes[0].quality
    }

    void 'text/xml and application/xml are one xml type, as preferred as the more preferred of the two and where the header lists the first, for #header'() {
        when:
        MimeType[] mimes = getAcceptHeaderParser().parse(header)

        then:
        mimes*.name == names
        mimes.find { it.name == 'application/xml' }.qualityAsNumber == xmlQuality

        where:
        header                                                          || names                                     | xmlQuality
        'text/xml, application/xml;q=0.1, application/json;q=0.5'       || ['application/xml', 'application/json']   | 1.0
        'text/xml;q=0.1, application/xml, application/json;q=0.5'       || ['application/xml', 'application/json']   | 1.0
        'text/xml;q=0.2, application/xml;q=0.1, application/json;q=0.5' || ['application/json', 'application/xml']   | 0.2
        'text/xml, application/json, application/xml;q=0.9'             || ['application/xml', 'application/json']   | 1.0
    }

    void 'a +xml type at least as preferred as application/xml, after it in the header, is promoted over it as in Grails 8'() {
        expect: 'atom takes the place of the xml before it, ahead of json, which is as preferred'
        getAcceptHeaderParser().parse('application/xml;q=0.9, application/json, application/atom+xml')*.extension ==
                ['atom', 'json', 'xml']

        and: 'of several, the one listed later goes ahead of those listed before it'
        getAcceptHeaderParser().parse('application/xml, application/atom+xml, application/rss+xml')*.extension ==
                ['rss', 'atom', 'xml']
    }

    void "a problem type is read as the format's own type whatever order the configuration lists its types in, #header"() {
        given:
        def atom = new MimeType('application/atom+xml', 'atom')
        def parser = new DefaultAcceptHeaderParser(types.collect { it == 'atom' ? atom : it } as MimeType[])

        expect:
        parser.parse(header)*.name == names

        where:
        types                                   | header                                                             || names
        [MimeType.TEXT_XML, MimeType.XML, 'atom'] | 'application/problem+xml, application/atom+xml'                    || ['application/atom+xml', 'application/xml']
        [MimeType.XML, MimeType.TEXT_XML, 'atom'] | 'application/problem+xml, application/atom+xml'                    || ['application/atom+xml', 'application/xml']
        [MimeType.TEXT_XML, MimeType.XML, MimeType.JSON] | 'text/xml;q=0.5, application/problem+xml, application/json;q=0.8' || ['application/xml', 'application/json']
        [MimeType.TEXT_JSON, MimeType.JSON]       | 'application/problem+json'                                         || ['application/json']
        [MimeType.TEXT_XML]                       | 'application/problem+xml'                                          || ['application/xml']
    }

    void 'a +xml type listed after the first of text/xml and application/xml takes its place, for #header'() {
        expect:
        getAcceptHeaderParser().parse(header)*.extension == extensions

        where:
        header                                                                  || extensions
        'text/xml, application/atom+xml, application/xml'                       || ['atom', 'xml']
        'text/xml, application/json, application/atom+xml, application/xml'    || ['atom', 'xml', 'json']
        'text/xml, application/atom+xml'                                        || ['xml', 'atom']
    }

    void 'a problem type listed ahead of another type counts as its format, with the default types, for #header'() {
        expect:
        new DefaultAcceptHeaderParser(MimeType.createDefaults()).parse(header)*.extension == extensions

        where:
        header                                                       || extensions
        'application/problem+json, application/hal+json'             || ['json', 'hal']
        'application/problem+xml, application/json'                  || ['xml', 'json']
        'text/xml, application/atom+xml, application/problem+xml'    || ['atom', 'xml']
    }

    void 'a bare * with parameters, which Java clients send, is not a match, as in Grails 8'() {
        expect:
        getAcceptHeaderParser().parse('text/html, image/gif, image/jpeg, *; q=.2, */*; q=.2')*.name == ['text/html', '*/*']
        getAcceptHeaderParser().parse('*;q=0.5')*.name == MimeType.createDefaults()*.name
    }

    protected DefaultAcceptHeaderParser getAcceptHeaderParser() {
        final application = new DefaultGrailsApplication()
        application.setConfig(config)
        final def mainContext = new GenericApplicationContext()
        mainContext.refresh()
        application.setApplicationContext(mainContext)

        def bb = new BeanBuilder()
        bb.beans {
            grailsApplication = application
            mimeConfiguration(MimeTypesConfiguration, application, [])
        }
        final ApplicationContext context = bb.createApplicationContext()
        final MimeTypesConfiguration mimeTypesConfiguration = context.getBean(MimeTypesConfiguration)
        final parser = new DefaultAcceptHeaderParser(mimeTypesConfiguration.mimeTypes())
        parser
    }
    
    void testXmlContentTypeWithCharsetAndVersion() {

        when:
        def mimes = getAcceptHeaderParser().parse("text/xml; charset=UTF-8; v=1.1")

        then:
        1 == mimes.size()
        "application/xml" == mimes[0].name
        "xml" == mimes[0].extension
        'UTF-8' == mimes[0].parameters.charset
        '1.0' == mimes[0].parameters.q
        '1.0' == mimes[0].quality
        '1.1' == mimes[0].version
    }

    void testGRAILS10678() {

        when:
        def mimes = getAcceptHeaderParser().parse("application/json;")

        then:
        1 == mimes.size()
        "application/json" == mimes[0].name
        "json" == mimes[0].extension
    }

    void 'a malformed token is left out, whatever empty parameters it has: #token'() {
        expect:
        getAcceptHeaderParser().parse("${token},application/json")*.extension == ['json']

        where:
        token << ['text/ html;', 'text/ html;;', ';', 'text/ html;=']
    }

    void 'a type with a parameter without a name or a value is kept: #token'() {
        expect:
        getAcceptHeaderParser().parse("${token},application/json")*.extension == ['html', 'json']

        where:
        token << ['text/html;level=', 'text/html;=1']
    }

    void testFirefox2AcceptHeaderOrdering() {

        when:
        def mimes = getAcceptHeaderParser().parse("text/xml,application/xml,application/xhtml+xml,text/html;q=0.9,text/plain;q=0.8,image/png,*/*;q=0.5")

        then:
        assertEquals 5, mimes.size()

        ['application/xhtml+xml','application/xml', 'text/html', 'text/plain', '*/*'] == mimes.name
        ['html', 'xml', 'html', 'text', 'all'] == mimes.extension
    }

    void testFirefox3AcceptHeaderOrdering() {

        when:
        def mimes = getAcceptHeaderParser().parse("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")

        then:
        4 == mimes.size()
        ['html','html','xml', 'all'] == mimes.extension
    }

    void testParseAcceptHeaderWithNonNumericQualityValue() {

        when:
        def mimes = getAcceptHeaderParser().parse("text/html,application/xhtml+xml,application/xml;q=blah,*/*;q=0.8")

        then:
        4 ==  mimes.size()
        ['html','html','xml', 'all'] == mimes.extension
    }

    void "retains header order when a lenient quality value precedes another equally ranked type"() {
        when:
        MimeType[] mimeTypes = getAcceptHeaderParser().parse('application/xml;q=invalid,text/html')

        then:
        mimeTypes*.extension == ['xml', 'html']
    }

    void "the problem types of RFC 9457 are the json and xml formats, in the order of their quality, for #header"() {
        expect:
        getAcceptHeaderParser().parse(header)*.extension == formats

        where:
        header                                                        || formats
        'application/problem+xml'                                     || ['xml']
        'application/problem+json'                                    || ['json']
        'application/problem+json;q=0.5, application/problem+xml'     || ['xml', 'json']
        'Application/Problem+XML'                                     || ['xml']
    }

    void "a problem type grails.mime.types configures is its own format"() {
        given:
        def parser = new DefaultAcceptHeaderParser(
                [new MimeType('application/problem+json', 'problem'), MimeType.JSON, MimeType.XML] as MimeType[])

        expect:
        parser.parse('application/problem+json')*.extension == ['problem']
        parser.parse('application/problem+xml')*.extension == ['xml']
    }

    void "the quality is read whatever the case of its name, #description, as RFC 9110 and Spring read it"() {
        expect:
        getAcceptHeaderParser().parse(header)*.extension == ['json', 'html']

        where:
        description                     | header
        'as Spring parses the header'   | 'text/html;Q=0.1, application/json'
        'as Grails parses what it does' | 'text/html;charset=no-such-charset;Q=0.1, application/json'
    }

    void "a lenient quality value is still recognised when a space precedes the parameter"() {
        when: "Spring rejects the header, so the lenient path parses it"
        MimeType[] mimeTypes = getAcceptHeaderParser().parse('application/json;q=1.5, text/html ; q=2.0')

        then: "the media type name is trimmed, so it still matches the configured types, and its quality orders it first"
        mimeTypes*.extension == ['html', 'json']
    }

    void testAcceptHeaderWithQNumberOrdering() {

        when:
        def mimes = getAcceptHeaderParser().parse("text/html,application/xhtml+xml,application/xml;q=1.1,*/*;q=0.8")

        then:
        4 == mimes.size()
        ['xml','html','html', 'all'] == mimes.extension
    }

    void testPrototypeHeaderOrdering() {
        when:
        def mimes = getAcceptHeaderParser().parse("text/javascript, text/html, application/xml, text/xml, */*")

        then:
        4 ==  mimes.size()
        ["js",'html', 'xml', 'all'] == mimes.extension
        ["text/javascript",'text/html', 'application/xml', '*/*'] == mimes.name
    }

    void testOldBrowserHeader() {
        when:
        def mimes = getAcceptHeaderParser().parse("*/*")

        then:
        1 == mimes.size()
        ['all'] == mimes.extension
    }

    // test for GRAILS-3389
    void testAcceptExtensionWithTokenNoValue() {
        when:
        def mimes = getAcceptHeaderParser().parse("text/html,application/xhtml+xml,application/xml;token,*/*;q=0.8")

        then:
        4 ==  mimes.size()
        ['html','html', 'xml','all'] == mimes.extension
    }

    // test for GRAILS-3493
    void testAcceptHeaderWithNoQValue() {
        when:
        def mimes = getAcceptHeaderParser().parse("application/xml; charset=UTF-8")

        then:
        1 == mimes.size()
    }

    void testAcceptExtensionWithCustomVndTypeAndVersion() {

        when:
        def mimesV1 = getAcceptHeaderParser().parse("application/vnd.foo+json;v=1.0; charset=UTF-8")
        def mimesV2 = getAcceptHeaderParser().parse("application/vnd.foo+json;v=2.0; charset=UTF-8")

        then:
        ['foov1'] == mimesV1.extension
        ['foov2'] == mimesV2.extension
    }

    void "matches a configured media type with capitals whatever the case of the header"() {
        expect:
        getAcceptHeaderParser().parse(header)*.extension == ['acme']
        getAcceptHeaderParser().parse(header)*.name == ['application/vnd.Acme+json']

        where:
        header << ['application/vnd.Acme+json', 'application/vnd.acme+json', 'APPLICATION/VND.ACME+JSON;q=0.5']
    }

    void "preserves configured header parameters on the negotiated mime type"() {
        when:
        MimeType[] mimeTypes = getAcceptHeaderParser().parse('application/json;charset=UTF-8;profile=compact;q=0.7')

        then:
        mimeTypes*.name == ['application/json']
        mimeTypes*.extension == ['json']
        mimeTypes[0].parameters == [q: '0.7', charset: 'UTF-8', profile: 'compact']
    }

    void "orders configured mime types by quality while retaining header order for equal quality"() {
        when:
        MimeType[] mimeTypes = getAcceptHeaderParser().parse(
                'text/plain;q=0.8,application/json;q=0.9,text/html;q=0.9,*/*;q=0.1'
        )

        then:
        mimeTypes*.name == ['application/json', 'text/html', 'text/plain', '*/*']
        mimeTypes*.extension == ['json', 'html', 'text', 'all']
    }

    void "filters unconfigured media types without disturbing configured choices"() {
        when:
        MimeType[] mimeTypes = getAcceptHeaderParser().parse(
                'image/avif,application/json;q=0.8,image/webp;q=0.7,text/plain;q=0.6'
        )

        then:
        mimeTypes*.name == ['application/json', 'text/plain']
        mimeTypes*.extension == ['json', 'text']
    }

    void "falls back to the configured media type when a requested vendor version is unknown"() {
        when:
        MimeType[] mimeTypes = getAcceptHeaderParser().parse('application/vnd.foo+json;v=3.0')

        then:
        mimeTypes*.name == ['application/vnd.foo+json']
        mimeTypes*.extension == ['foov1']
        mimeTypes*.version == ['3.0']
    }
}
