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
}
