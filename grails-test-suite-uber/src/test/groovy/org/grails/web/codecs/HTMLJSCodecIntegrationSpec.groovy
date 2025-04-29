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

import grails.core.GrailsApplication;
import grails.util.GrailsWebMockUtil

import org.grails.buffer.FastStringWriter
import org.grails.commons.DefaultGrailsCodecClass
import org.grails.commons.GrailsCodecClass
import org.grails.encoder.EncodingStateRegistry
import org.grails.encoder.impl.HTMLJSCodec
import org.grails.encoder.impl.JavaScriptCodec
import org.grails.encoder.impl.RawCodec
import org.grails.plugins.codecs.HTMLCodec
import org.grails.web.servlet.mvc.GrailsWebRequest

import spock.lang.Specification
import spock.lang.Unroll

public class HTMLJSCodecIntegrationSpec extends Specification {
    GrailsCodecClass htmlCodecClass
    GrailsCodecClass rawCodecClass
    GrailsCodecClass jsCodecClass
    GrailsCodecClass htmlJsCodecClass
    EncodingStateRegistry registry
    
    def setup() {
        def grailsApplication = Mock(GrailsApplication)
        htmlJsCodecClass = new DefaultGrailsCodecClass(HTMLJSCodec)
        grailsApplication.getArtefact("Codec", HTMLJSCodec.name) >> { htmlJsCodecClass }
        htmlCodecClass = new DefaultGrailsCodecClass(HTMLCodec)
        grailsApplication.getArtefact("Codec", HTMLCodec.name) >> { htmlCodecClass }
        rawCodecClass = new DefaultGrailsCodecClass(RawCodec)
        grailsApplication.getArtefact("Codec", RawCodec.name) >> { rawCodecClass }
        jsCodecClass = new DefaultGrailsCodecClass(JavaScriptCodec)
        grailsApplication.getArtefact("Codec", JavaScriptCodec.name) >> { jsCodecClass }
        def codecClasses = [htmlCodecClass, htmlJsCodecClass, rawCodecClass, jsCodecClass]
        grailsApplication.getCodecClasses() >> { codecClasses }
        codecClasses*.configureCodecMethods()
        GrailsWebMockUtil.bindMockWebRequest()
        registry = GrailsWebRequest.lookup().getEncodingStateRegistry()
    }
    
    @Unroll
    def "do streaming html and js encoding - prevent double encoding - preEncoded:#preEncoded"(boolean preEncoded) {
        given:
            def target = new FastStringWriter()
            def writer = target.getWriterForEncoder(htmlJsCodecClass.encoder, registry)
        when:
            writer << (preEncoded ? htmlCodecClass.encoder.encode("<script>") : "<script>")
            writer.flush()
        then:
            target.toString() == '\\u0026lt\\u003bscript\\u0026gt\\u003b'
        where:
            preEncoded << [true, false]
    }
}
