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
package org.grails.encoder;

import groovy.json.StringEscapeUtils

import org.grails.encoder.impl.HTMLEncoder
import org.grails.encoder.impl.JavaScriptEncoder
import org.grails.buffer.StreamCharBuffer
import org.springframework.web.util.HtmlUtils

import spock.lang.Specification


class ChainedEncodersSpec extends Specification {
    def "should support encoding with one encoder"() {
        given:
            def encoders = [new HTMLEncoder()]
            def source = new StreamCharBuffer()
            source.writer.write('<1> Hello World;')
        when:
            def resultBuffer = new StreamCharBuffer()
            resultBuffer.setAllowSubBuffers(false)
            ChainedEncoders.chainEncode(source, resultBuffer.writer.encodedAppender, encoders)
        then:
            def resultStr = resultBuffer.toString()
            def unescapedStr = HtmlUtils.htmlUnescape(resultStr)
            resultStr == '&lt;1&gt; Hello World;'
            resultStr != unescapedStr
    }

    def "chaining StreamingEncoders should be possible"() {
        given:
            def encoders = [new HTMLEncoder(), new JavaScriptEncoder()]
            def source = new StreamCharBuffer()
            source.writer.write('<1> Hello World;')
        when:
            def resultBuffer = new StreamCharBuffer()
            resultBuffer.setAllowSubBuffers(false)
            ChainedEncoders.chainEncode(source, resultBuffer.writer.encodedAppender, encoders)
        then:
            def resultStr = resultBuffer.toString()
            def unescapedStr = StringEscapeUtils.unescapeJavaScript(resultStr) 
            resultStr != unescapedStr
            unescapedStr == '&lt;1&gt; Hello World;'
    }
    
    def "chaining Encoders (mixed) should be possible"() {
        given:
            def encoders = [new HTMLEncoder(), new MyJavaScriptEncoder()]
            def source = new StreamCharBuffer()
            source.writer.write('<1> Hello World;')
        when:
            def resultBuffer = new StreamCharBuffer()
            resultBuffer.setAllowSubBuffers(false)
            ChainedEncoders.chainEncode(source, resultBuffer.writer.encodedAppender, encoders)
        then:
            def resultStr = resultBuffer.toString()
            def unescapedStr = StringEscapeUtils.unescapeJavaScript(resultStr)
            resultStr != unescapedStr
            unescapedStr == '&lt;1&gt; Hello World;'
    }
    
    class MyJavaScriptEncoder implements Encoder {
        JavaScriptEncoder jsEncoder = new JavaScriptEncoder()
        boolean safe=true
        boolean applyToSafelyEncoded=true
        
        @Override
        public CodecIdentifier getCodecIdentifier() {
            new DefaultCodecIdentifier("myJs")
        }

        @Override
        public Object encode(Object o) {
            return jsEncoder.encode(o)
        }
        
        @Override
        public void markEncoded(CharSequence string) {
            
        }
    }
}
