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
package org.grails.encoder.impl

import org.grails.encoder.EncodedAppender
import org.grails.encoder.EncodingState
import org.grails.encoder.StreamingEncoder

import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

class HTMLEncoderSpec extends Specification {
    def "html encoding should support streaming interface"() {
        given:
            def encoder=new HTMLEncoder()
        expect:
            encoder instanceof StreamingEncoder
    }

    @Unroll
    def "streaming should encode longest part at a time for #streamingEncoder.codecIdentifier.codecName codec"(StreamingEncoder streamingEncoder) {
        given:
             EncodedAppender appender=Mock(EncodedAppender)
             EncodingState encodingState=Mock(EncodingState)
             def hello="Hello <script>alert('hi!')</script> World!"
        when:
            streamingEncoder.encodeToStream(streamingEncoder, hello, 0, hello.length(), appender, encodingState)
        then:
            1 * appender.appendEncoded(streamingEncoder, encodingState, hello, 0, 6)
        then:
            1 * appender.appendEncoded(streamingEncoder, encodingState, '&lt;', 0, 4)
        then:
            1 * appender.appendEncoded(streamingEncoder, encodingState, hello, 7, 6)
        then:
            1 * appender.appendEncoded(streamingEncoder, encodingState, '&gt;', 0, 4)
        then:
            1 * appender.appendEncoded(streamingEncoder, encodingState, hello, 14, 6)
        then:
            1 * appender.appendEncoded(streamingEncoder, encodingState, '&#39;', 0, 5)
        then:
            1 * appender.appendEncoded(streamingEncoder, encodingState, hello, 21, 3)
        then:
            1 * appender.appendEncoded(streamingEncoder, encodingState, '&#39;', 0, 5)
        then:
            1 * appender.appendEncoded(streamingEncoder, encodingState, hello, 25, 1)
        then:
            1 * appender.appendEncoded(streamingEncoder, encodingState, '&lt;', 0, 4)
        then:
            1 * appender.appendEncoded(streamingEncoder, encodingState, hello, 27, 7)
        then:
            1 * appender.appendEncoded(streamingEncoder, encodingState, '&gt;', 0, 4)
        then:
            1 * appender.appendEncoded(streamingEncoder, encodingState, hello, 35, 7)
            0 * _
        where:
            streamingEncoder << [new HTMLEncoder(), new HTML4Encoder(), new BasicXMLEncoder()]
    }
    
    @Issue("GRAILS-10684")
    def "html encoder shouldn't throw NPE when toString() returns null"() {
        given:
            def encoder=new HTMLEncoder()
        expect:
            encoder.encode(new ToStringNull())==null
    }
}

class ToStringNull {
     public String toString() { null }   
}
