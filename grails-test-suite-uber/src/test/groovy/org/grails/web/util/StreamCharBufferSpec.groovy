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
package org.grails.web.util

import grails.core.GrailsApplication
import grails.util.GrailsWebMockUtil

import org.grails.buffer.GrailsPrintWriter
import org.grails.buffer.StreamCharBuffer
import org.grails.commons.DefaultGrailsCodecClass
import org.grails.commons.GrailsCodecClass
import org.grails.encoder.EncoderAware
import org.grails.encoder.EncodesToWriterAdapter
import org.grails.encoder.EncodingStateRegistryLookupHolder
import org.grails.encoder.impl.HTML4Codec
import org.grails.encoder.impl.JavaScriptCodec
import org.grails.encoder.impl.RawCodec
import org.grails.plugins.codecs.HTMLCodec

import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

class StreamCharBufferSpec extends Specification {
    StreamCharBuffer buffer
    GrailsPrintWriter codecOut
    GrailsPrintWriter out
    GrailsCodecClass htmlCodecClass
    GrailsCodecClass rawCodecClass
    GrailsCodecClass jsCodecClass

    def setup() {
        def grailsApplication = Mock(GrailsApplication)
        htmlCodecClass = new DefaultGrailsCodecClass(HTMLCodec)
        grailsApplication.getArtefact("Codec", HTMLCodec.name) >> { htmlCodecClass }
        GrailsCodecClass html4CodecClass = new DefaultGrailsCodecClass(HTML4Codec)
        grailsApplication.getArtefact("Codec", HTML4Codec.name) >> { html4CodecClass }
        rawCodecClass = new DefaultGrailsCodecClass(RawCodec)
        grailsApplication.getArtefact("Codec", RawCodec.name) >> { rawCodecClass }
        jsCodecClass = new DefaultGrailsCodecClass(JavaScriptCodec) 
        grailsApplication.getArtefact("Codec", JavaScriptCodec.name) >> { jsCodecClass }
        def codecClasses = [htmlCodecClass, html4CodecClass, rawCodecClass, jsCodecClass]
        grailsApplication.getCodecClasses() >> { codecClasses }
        GrailsWebMockUtil.bindMockWebRequest()

        buffer=new StreamCharBuffer()
        out=new GrailsPrintWriter(buffer.writerForEncoder)

        codecClasses*.configureCodecMethods()
        codecOut=new GrailsPrintWriter(out.getWriterForEncoder(htmlCodecClass.encoder, EncodingStateRegistryLookupHolder.getEncodingStateRegistryLookup().lookup()))
    }

    def "stream char buffer should support encoding"() {
        when:
        def hello="Hello world & hi".encodeAsHTML()
        codecOut << hello
        then:
        buffer.toString() == "Hello world &amp; hi"
    }

    def "stream char buffer should support connecting to writer"() {
        given:
        def connectedBuffer=new StreamCharBuffer()
        buffer.connectTo(connectedBuffer.writer, true)
        when:
        def hello="Hello world & hi"
        codecOut << hello
        codecOut.flush()
        then:
        connectedBuffer.toString() == "Hello world &amp; hi"
    }

    def "stream char buffer should support automaticly encoding to connected writer"() {
        given:
        def connectedBuffer=new StreamCharBuffer()
        buffer.encodeInStreamingModeTo([getEncoder: { -> htmlCodecClass.encoder}] as EncoderAware, EncodingStateRegistryLookupHolder.getEncodingStateRegistryLookup(), true, connectedBuffer.writer)
        when:
        def hello="Hello world & hi"
        out << hello
        out.flush()
        then:
        connectedBuffer.toString() == "Hello world &amp; hi"
    }

    def "double encoding should be prevented"() {
        when:
        def hello="Hello world & hi".encodeAsHTML().encodeAsHTML()
        codecOut << hello
        then:
        hello == "Hello world &amp; hi"
        buffer.toString() == "Hello world &amp; hi"
    }

    def "prevent double encoding of joined buffers"() {
        when:
        def hello="Hello world & hi".encodeAsHTML()
        def buffer2=new StreamCharBuffer()
        def out2=new GrailsPrintWriter(buffer2.writerForEncoder)
        out2 << hello
        out2 << "<script>"
        codecOut << buffer2
        then:
        buffer.toString() == "Hello world &amp; hi&lt;script&gt;"
    }

    def "prevent double encoding of toStringed buffer"() {
        when:
        def hello="Hello world & hi"
        def buffer2=new StreamCharBuffer()
        buffer2.writerForEncoder << hello
        buffer2.writerForEncoder.flush()
        def buffer3=new StreamCharBuffer()
        def helloEncoded = buffer2.encodeAsHTML().toString()
        buffer3.writerForEncoder << helloEncoded
        buffer3.writerForEncoder << "<script>"
        codecOut << buffer3
        then:
        buffer.toString() == "Hello world &amp; hi&lt;script&gt;"
    }

    def "prevent double encoding of SCBs"() {
        when:
        def hello="Hello world & hi"
        def buffer2=new StreamCharBuffer()
        def writer = new GrailsPrintWriter(buffer2.writerForEncoder)
        writer << hello
        writer.flush()
        def buffer3=new StreamCharBuffer()
        def helloEncoded = buffer2.encodeAsHTML().encodeAsHTML()
        def writer2 = new GrailsPrintWriter(buffer3.writerForEncoder)
        writer2 << helloEncoded
        writer2 << "<script>"
        codecOut << buffer3
        then:
        helloEncoded.toString() == "Hello world &amp; hi"
        buffer.toString() == "Hello world &amp; hi&lt;script&gt;"
    }

    def "support raw codec"() {
        when:
        def hello="Hello world & hi"
        def buffer2=new StreamCharBuffer()
        def writer = new GrailsPrintWriter(buffer2.writerForEncoder)
        writer << hello
        writer.flush()
        def buffer3=new StreamCharBuffer()
        def helloEncoded = buffer2.encodeAsRaw().encodeAsHTML()
        def writer2 = new GrailsPrintWriter(buffer3.writerForEncoder)
        writer2 << helloEncoded
        writer2 << "<script>"
        codecOut << buffer3
        then:
        helloEncoded.toString() == "Hello world & hi"
        buffer.toString() == "Hello world & hi&lt;script&gt;"
    }

    def "single quotes must be escaped"() {
        when:
            def hello="Hello 'Grails'".encodeAsHTML4()
            def hello2="Hello 'Grails'".encodeAsHTML()
        then:
            hello.toString()=="Hello &#39;Grails&#39;"
            hello2.toString()=="Hello &#39;Grails&#39;"
    }

    def "toString should keep encoding state"() {
        when:
        codecOut << "<script>".encodeAsHTML()
        codecOut << "Hello world & hi".encodeAsHTML()
        then:
        buffer.encodeAsHTML().toString() == "&lt;script&gt;Hello world &amp; hi"
        buffer.toString() == "&lt;script&gt;Hello world &amp; hi"
        buffer.toString() == "&lt;script&gt;Hello world &amp; hi"
        buffer.toString().encodeAsHTML().toString() == "&lt;script&gt;Hello world &amp; hi"
        buffer.toString().encodeAsHTML().encodeAsHTML().toString() == "&lt;script&gt;Hello world &amp; hi"
    }

    def "encodeAsRaw should prevent other encodings"() {
        when:
        out << "<script>"
        out << "Hello world & hi".encodeAsRaw()
        then:
        buffer.encodeAsHTML().toString() == "&lt;script&gt;Hello world & hi"
    }

    def "toString should keep encoding state when no codec"() {
        when:
        out << "<script>"
        out << "Hello world & hi".encodeAsRaw()
        then:
        buffer.encodeAsHTML().toString() == "&lt;script&gt;Hello world & hi"
        buffer.encodeAsHTML().encodeAsHTML().toString() == "&lt;script&gt;Hello world & hi"
        buffer.toString() == "<script>Hello world & hi"
        buffer.toString() == "<script>Hello world & hi"
        buffer.encodeAsHTML().toString() == "&lt;script&gt;Hello world & hi"
        buffer.encodeAsHTML().encodeAsHTML().toString() == "&lt;script&gt;Hello world & hi"
    }

    def "serialization should keep encoding state"() {
        when:
        codecOut << "Hello world & hi".encodeAsRaw()
        codecOut << "<script>"
        then:
        buffer.toString() == "Hello world & hi&lt;script&gt;"
        when:
        ByteArrayOutputStream bos = new ByteArrayOutputStream()
        ObjectOutputStream out = new ObjectOutputStream(bos)
        out.writeObject(buffer)
        out.close()
        byte[] bytes = bos.toByteArray()
        ObjectInputStream inp = new ObjectInputStream(new ByteArrayInputStream(bytes))
        StreamCharBuffer bufferUnserialized = (StreamCharBuffer) inp.readObject()
        StreamCharBuffer bufferUnserializedHtml = bufferUnserialized.encodeAsHTML()
        then:
        bufferUnserializedHtml.toString() == "Hello world & hi&lt;script&gt;"
    }

    def "clone should keep encoding state"() {
        when:
        codecOut << "Hello world & hi".encodeAsRaw()
        codecOut << "<script>"
        then:
        buffer.toString() == "Hello world & hi&lt;script&gt;"
        when:
        StreamCharBuffer bufferCloned = buffer.clone()
        StreamCharBuffer bufferClonedHtml = bufferCloned.encodeAsHTML()
        then:
        bufferClonedHtml.toString() == "Hello world & hi&lt;script&gt;"
    }
    
    @Issue("GRAILS-10765")
    def "empty SCB shouldn't throw NPE when toCharArray() is called"() {
        given:
            StreamCharBuffer buffer=new StreamCharBuffer()
        expect:
            buffer.toCharArray() == [] as char[]
    }
    
    @Issue("GRAILS-11507")
    def "should SCB provide optimal streaming method to target Writer"() {
        given:
        def mockWriter = Mock(Writer)
        when:
        codecOut << "<Raw codec".encodeAsRaw()
        codecOut << "<another part"
        codecOut << "<script>".encodeAsHTML()
        codecOut << "Hello world & hi".encodeAsHTML()
        def encodesToWriter = new EncodesToWriterAdapter(htmlCodecClass.encoder)
        buffer.encodeTo(mockWriter, encodesToWriter) 
        then:
        1 * mockWriter.write(_, 0, 10)
        1 * mockWriter.write(_, 10, 50)
        0 * _._
    }
    
    @Issue("GRAILS-11507")
    def "should SCB provide optimal streaming method to target Writer and return correct results"() {
        given:
        def stringWriter = new StringWriter()
        when:
        codecOut << "<Raw codec".encodeAsRaw()
        codecOut << "<another part"
        codecOut << "<script>".encodeAsHTML()
        codecOut << "Hello world & hi".encodeAsHTML()
        def encodesToWriter = new EncodesToWriterAdapter(htmlCodecClass.encoder)
        buffer.encodeTo(stringWriter, encodesToWriter)
        then:
        stringWriter.toString() == '<Raw codec&lt;another part&lt;script&gt;Hello world &amp; hi'
    }
    
    @Issue("GRAILS-11505")
    @Unroll
    def "should SCB support preferSubChunkWhenWritingToOtherBuffer feature - #resetLastBuffer"(boolean resetLastBuffer) {
        given:
            int bufid=1
            def buffers = [:]
            def msgs = [:]
            def createSubBufferClosure = {
                def subbufid = bufid++
                def msg = "Hello world <#${subbufid}>"
                def subbuf = createSubBuffer { out ->
                    out << msg
                }
                buffers.put(subbufid, subbuf)
                msgs.put(subbufid, msg)
                subbuf
            }
            20.times(createSubBufferClosure)
            String expectedMessage = ""
            String expectedWithoutExtra = ""
            def secondLevelSubBuf = createSubBuffer { out -> out << '<script>from subbuffer</script>' }
            secondLevelSubBuf.preferSubChunkWhenWritingToOtherBuffer = true
            def encodedSecondLevelSubBuf = secondLevelSubBuf.encodeToBuffer(htmlCodecClass.encoder, true, true)
            assert encodedSecondLevelSubBuf.clone().toString() == '&lt;script&gt;from subbuffer&lt;/script&gt;'
            encodedSecondLevelSubBuf.notifyParentBuffersEnabled = true
            encodedSecondLevelSubBuf.preferSubChunkWhenWritingToOtherBuffer = true
            def secondOut = new GrailsPrintWriter(buffers.get(10).writer)
            secondOut << encodedSecondLevelSubBuf
            secondOut.close()
            def allbuffers = createSubBuffer { out ->
                buffers.each { k, v ->
                    out << v
                    expectedMessage += msgs.get(k)
                    expectedWithoutExtra += msgs.get(k)
                    if(k == 10) {
                        expectedMessage += '&lt;script&gt;from subbuffer&lt;/script&gt;'
                    }
                }
            }
        expect:
            buffers.get(10).preferSubChunkWhenWritingToOtherBuffer == true
            buffers.get(10).clone().toString() == 'Hello world <#10>&lt;script&gt;from subbuffer&lt;/script&gt;'
            allbuffers.size() == expectedMessage.length()
            allbuffers.clone().size() == expectedMessage.length()
            with(allbuffers.clone()) { obj ->
                obj.toString() == expectedMessage
                obj.length() == expectedMessage.length()
                obj.toString() == expectedMessage
                obj.length() == expectedMessage.length()
            }
            allbuffers.size() == expectedMessage.length()
        when:
            if(resetLastBuffer) {
                secondLevelSubBuf.clear()
            } else {
                encodedSecondLevelSubBuf.clear()
            }
        then:
            def withoutExtra = allbuffers.clone().toString()
            withoutExtra.trim() == expectedWithoutExtra.trim()
            allbuffers.size() == expectedWithoutExtra.length()
            allbuffers.clone().size() == expectedWithoutExtra.length()
            withoutExtra.length() == expectedWithoutExtra.length()
            withoutExtra == expectedWithoutExtra
        where:
            resetLastBuffer << [true, false]
    }
    
    private def createSubBuffer(Closure outputClosure) {
        StreamCharBuffer scb = new StreamCharBuffer()
        def pw = new GrailsPrintWriter(scb.writer)
        outputClosure(pw)
        pw.close()
        scb
    }
    
    @Unroll
    @Issue("GRAILS-11505")
    def "should support multiple levels of encoded subbuffers - #callSize #callFlush"(boolean callSize, boolean callFlush) {
        given:
            def scb = createSubBuffer { out ->
                out << "<1> - Hello;"
            }
            scb.preferSubChunkWhenWritingToOtherBuffer = true
        expect:
            scb.toString() == '<1> - Hello;'
        when:
            def scb2 = jsCodecClass.encoder.encode(scb)
        then:
            if(callSize) {
                // size and clone have side-effects in SCB, so we want to test both paths
                scb2.size() == 27
                scb2.clone().toString() == '\\u003c1\\u003e - Hello\\u003b'
            }
        when:
            scb.writer.write '(1)'
            if(callFlush) {
                scb.writer.flush()
            }
        then:
            if(callFlush) {
                scb2.toString() == '\\u003c1\\u003e - Hello\\u003b\\u00281\\u0029'
                scb2.size() == 40
            } else {
                scb2.toString() == '\\u003c1\\u003e - Hello\\u003b'
                scb2.size() == 27
            }
            scb.toString() == '<1> - Hello;(1)'
        where:
            [callSize, callFlush] << [[true, false], [true, false]].combinations()*.flatten()
    }

}
