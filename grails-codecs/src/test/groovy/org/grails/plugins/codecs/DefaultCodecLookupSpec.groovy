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

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import org.grails.commons.GrailsCodecClass
import org.grails.encoder.Encoder

class DefaultCodecLookupSpec extends Specification {

    void 'a codec lookup requires a grails application and exposes it through the setter'() {
        when:
        new DefaultCodecLookup(null)

        then:
        thrown(NullPointerException)

        when:
        DefaultCodecLookup lookup = new DefaultCodecLookup()
        lookup.setGrailsApplication(null)

        then:
        thrown(NullPointerException)
    }

    void 'registerCodecs scans codec artefacts and registers them in reverse priority order'() {
        given:
        DefaultGrailsApplication application = new DefaultGrailsApplication(DclHighCodec, DclLowCodec)
        application.initialise()
        DefaultCodecLookup lookup = new DefaultCodecLookup(application)

        when:
        lookup.registerCodecs()
        Encoder encoder = lookup.lookupEncoder('DclHigh')

        then:
        encoder != null
        encoder.encode('x') == 'high'
        lookup.lookupEncoder('DclLow').encode('x') == 'low'
    }

    void 'registerCodec configures the codec methods before registering the factory'() {
        given:
        DefaultCodecLookup lookup = new DefaultCodecLookup()
        boolean configured = false
        Encoder encoder = Stub(Encoder) {
            getCodecIdentifier() >> new org.grails.encoder.DefaultCodecIdentifier('DclStub')
        }
        GrailsCodecClass codecClass = Stub(GrailsCodecClass) {
            configureCodecMethods() >> { configured = true }
            getEncoder() >> encoder
            getDecoder() >> null
        }

        when:
        lookup.registerCodec(codecClass)

        then:
        configured
    }

}

class DclHighCodec {

    static order = 1
    static encode = { 'high' }

}

class DclLowCodec {

    static order = 2
    static encode = { 'low' }

}
