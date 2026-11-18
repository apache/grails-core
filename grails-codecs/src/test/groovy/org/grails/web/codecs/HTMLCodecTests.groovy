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
package org.grails.web.codecs

import grails.core.DefaultGrailsApplication
import org.grails.encoder.Encoder
import org.grails.plugins.codecs.HTMLCodec
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals

class HTMLCodecTests {

    private static final String SPECIAL_CHARACTERS = "@ ` \\ \u2028 Vid\u00E9o"
    private static final String XML_ENCODED = '&#64; &#96; &#92; &#8232; Vid\u00E9o'
    private static final String HTML4_ENCODED = "@ ` \\ \u2028 Vid&eacute;o"

    private static Encoder getEncoder(String htmlCodecSetting) {
        def htmlCodec = new HTMLCodec()
        def grailsApplication = new DefaultGrailsApplication()
        if (htmlCodecSetting != null) {
            grailsApplication.config[HTMLCodec.CONFIG_PROPERTY_GSP_HTMLCODEC] = htmlCodecSetting
            grailsApplication.configChanged()
        }
        htmlCodec.setGrailsApplication(grailsApplication)
        htmlCodec.afterPropertiesSet()
        return htmlCodec.getEncoder()
    }

    def getDecoder() {
        def htmlCodec = new HTMLCodec()
        def grailsApplication = new DefaultGrailsApplication()
        htmlCodec.setGrailsApplication(grailsApplication)
        htmlCodec.afterPropertiesSet()
        return htmlCodec.getDecoder()
    }

    @Test
    void testEncodeXml() {
        def encoder = getEncoder('xml')
        assertEquals('&lt;tag&gt;', encoder.encode('<tag>'))
        assertEquals('&quot;quoted&quot;', encoder.encode('"quoted"'))
        assertEquals("Hitchiker&#39;s Guide", encoder.encode("Hitchiker's Guide"))
        assertEquals("Vid\u00E9o", encoder.encode("Vid\u00E9o"))
        assertEquals(XML_ENCODED, encoder.encode(SPECIAL_CHARACTERS))
    }

    @Test
    void testEncodeHtml4() {
        def encoder = getEncoder('html4')
        assertEquals('&lt;tag&gt;', encoder.encode('<tag>'))
        assertEquals('&quot;quoted&quot;', encoder.encode('"quoted"'))
        assertEquals("Hitchiker&#39;s Guide", encoder.encode("Hitchiker's Guide"))
        assertEquals("Vid&eacute;o", encoder.encode("Vid\u00E9o"))
        assertEquals(HTML4_ENCODED, encoder.encode(SPECIAL_CHARACTERS))
    }

    @Test
    void testDefaultIsXmlEncoder() {
        assertEquals(XML_ENCODED, getEncoder(null).encode(SPECIAL_CHARACTERS))
        assertEquals(XML_ENCODED, new HTMLCodec().getEncoder().encode(SPECIAL_CHARACTERS))
    }

    @Test
    void testSettingsSelectingXmlEncoder() {
        for (setting in ['xml', 'XML', 'xhtml', 'XHTML', 'xml-safe', '', 'unknown']) {
            assertEquals(XML_ENCODED, getEncoder(setting).encode(SPECIAL_CHARACTERS), "htmlcodec: '${setting}'")
        }
    }

    @Test
    void testSettingsSelectingHtml4Encoder() {
        for (setting in ['html4', 'HTML4', 'html', 'HTML']) {
            assertEquals(HTML4_ENCODED, getEncoder(setting).encode(SPECIAL_CHARACTERS), "htmlcodec: '${setting}'")
        }
    }

    @Test
    void testUseLegacyEncoder() {
        def htmlCodec = new HTMLCodec()
        htmlCodec.setUseLegacyEncoder(true)
        assertEquals(HTML4_ENCODED, htmlCodec.getEncoder().encode(SPECIAL_CHARACTERS))
        htmlCodec.setUseLegacyEncoder(false)
        assertEquals(XML_ENCODED, htmlCodec.getEncoder().encode(SPECIAL_CHARACTERS))
    }

    @Test
    void testEncodersShareHtmlCodecIdentifier() {
        assertEquals('HTML', getEncoder(null).getCodecIdentifier().getCodecName())
        assertEquals('HTML', getEncoder('html4').getCodecIdentifier().getCodecName())
    }

    @Test
    void testDecode() {
        def decoder = getDecoder()
        assertEquals('<tag>', decoder.decode('&lt;tag&gt;'))
        assertEquals('"quoted"', decoder.decode('&quot;quoted&quot;'))
    }
}
