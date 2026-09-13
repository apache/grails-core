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
package org.grails.plugins.codecs

import spock.lang.Specification
import spock.lang.Unroll

import grails.config.Config
import grails.core.GrailsApplication
import org.grails.encoder.impl.HTML4Encoder
import org.grails.encoder.impl.HTMLEncoder

class HTMLCodecConfigurationSpec extends Specification {

    void 'a codec with no grails application or config is left at its legacy default'() {
        given:
        HTMLCodec codec = new HTMLCodec()

        expect:
        codec.encoder instanceof HTML4Encoder
        codec.decoder != null

        when:
        codec.afterPropertiesSet()

        then:
        codec.encoder instanceof HTML4Encoder

        when:
        codec.setGrailsApplication(Stub(GrailsApplication) { getConfig() >> null })
        codec.afterPropertiesSet()

        then:
        codec.encoder instanceof HTML4Encoder
    }

    @Unroll
    void 'a #setting htmlcodec setting selects the xml encoder'() {
        given:
        HTMLCodec codec = new HTMLCodec()
        Config config = Stub(Config) { getProperty(HTMLCodec.CONFIG_PROPERTY_GSP_HTMLCODEC) >> setting }
        codec.setGrailsApplication(Stub(GrailsApplication) { getConfig() >> config })

        when:
        codec.afterPropertiesSet()

        then:
        codec.encoder instanceof HTMLEncoder

        where:
        setting << ['xml', 'XML', 'xhtml', 'XHTML']
    }

    void 'a non-xml setting keeps the legacy encoder'() {
        given:
        HTMLCodec codec = new HTMLCodec()
        Config config = Stub(Config) { getProperty(HTMLCodec.CONFIG_PROPERTY_GSP_HTMLCODEC) >> 'html4' }
        codec.setGrailsApplication(Stub(GrailsApplication) { getConfig() >> config })

        when:
        codec.afterPropertiesSet()

        then:
        codec.encoder instanceof HTML4Encoder
    }

    void 'setUseLegacyEncoder toggles between the shared static encoder instances'() {
        given:
        HTMLCodec codec = new HTMLCodec()

        when:
        codec.setUseLegacyEncoder(false)

        then:
        codec.encoder == HTMLCodec.xml_encoder

        when:
        codec.setUseLegacyEncoder(true)

        then:
        codec.encoder == HTMLCodec.html4_encoder
    }

}
