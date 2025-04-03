package org.grails.web.codecs

import grails.converters.XML
import grails.converters.JSON
import grails.testing.web.GrailsWebUnitTest
import org.grails.buffer.StreamCharBuffer
import org.grails.plugins.codecs.JSONCodec
import org.grails.plugins.codecs.XMLCodec
import spock.lang.Issue
import spock.lang.Specification

/**
 * Tests the behavior of the include tag
 */
class CodecSpec extends Specification implements GrailsWebUnitTest {
    // TODO: separate tag codec from scriplet codec to it's own setting
    // TODO: applyCodec should have an option to make everything safe at the end

    void "safe codec should allow applying unsafe codecs"() {
        expect:
            '"<script>"'.encodeAsJavaScript() == '\\u0022\\u003cscript\\u003e\\u0022'
            '"<script>"'.encodeAsJavaScript().encodeAsURL() == '%5Cu0022%5Cu003cscript%5Cu003e%5Cu0022'
    }

    void "javascript codec should escape any safe codec"() {
        expect:
            '"<script>"'.encodeAsHTML() == '&quot;&lt;script&gt;&quot;'
            '"<script>"'.encodeAsHTML().encodeAsJavaScript() == '\\u0026quot\\u003b\\u0026lt\\u003bscript\\u0026gt\\u003b\\u0026quot\\u003b'
    }

    void "html codec should not escape a safe codec"() {
        expect:
            '"<script>"'.encodeAsJavaScript() == '\\u0022\\u003cscript\\u003e\\u0022'
            '"<script>"'.encodeAsJavaScript().encodeAsHTML() == '"<script>"'.encodeAsJavaScript()
    }
    
    @Issue("GRAILS-10980")
    void "JSON codec behaviour like in Grails versions pre 2.3.x"() {
        given:
            mockCodec(JSONCodec)
        when:
            String x=null
        then:
            [a: 1, b: 2, c: 3].encodeAsJSON().toString() == '{"a":1,"b":2,"c":3}'
            x.encodeAsJSON() == null
            1.encodeAsJSON() == '1' // convert primitives to string
            true.encodeAsJSON() == 'true'
    }

    @Issue("GRAILS-10980")
    void "XML codec behaviour like in Grails versions pre 2.3.x"() {
        given:
            mockCodec(XMLCodec)
        when:
            String x=null
        then:
            [a: 1, b: 2, c: 3].encodeAsXML().toString() == '<?xml version="1.0" encoding="UTF-8"?><map><entry key="a">1</entry><entry key="b">2</entry><entry key="c">3</entry></map>'
            x.encodeAsXML() == null
            1.encodeAsXML() == '1' // convert primitives to string
            true.encodeAsXML() == 'true'
    }
    
    @Issue("GRAILS-11493")
    void "should XML object support encodeAsXML method and return itself"() {
        given:
            def xml = [a: 1, b: 2, c: 3].encodeAsXML()
        expect:
            xml instanceof XML
        when:
            def result = xml.encodeAsXML()
        then:
            result == xml
    }  

    void "Test that the raw method works in GSP"() {
        when:"The raw method is called for a GSP expression"
            def content = applyTemplate('${foo}${raw(bar)}', [foo:'"<script>"', bar:'<script>'])
        then:"The content it output is raw form"
            content == '&quot;&lt;script&gt;&quot;<script>'
    }
    
    @Issue("GRAILS-11078")
    void "encodeAsHTML should not call the equals method of the object"() {
        given:
            def sample=new MySample()
        when:
            def result=sample.encodeAsHTML()
        then:
            result == 'Hello'
    }
    
    private static class MySample {
        @Override
        public String toString() {
            return 'Hello';
        }
        
        @Override
        public boolean equals(Object obj) {
            throw new RuntimeException("equals shouldn't be called")
        }
        }
    
    @Issue("GRAILS-11361")
    void "JSON converter should not use encoding state"() {
        given:
            def buffer=new StreamCharBuffer()
            buffer.writer.write('"Hello world"')
            def content=buffer.encodeAsRaw()
        when:
            def json = [content: content] as JSON
        then:
            json.toString() == '{"content":"\\"Hello world\\""}'
    } 

//    void "output should be safe at the end"() {
//    }
//
//    void "detailed test document showing a GSP + Taglib that uses a default HTML codec but also writes out JS data inline in the GSP, and writes out JS data inline using a call to a TagLib, and a call to a tag that renders pre-escaped HTML content, and so on"() {
//    }

//    // opionated setting
//    void "tag output must not be automatically encoded."() {
//        // TODO: problem with out << body() ?
//    }

//    /*
//     * static defaultEncodeAs = 'raw' // default encodeAs applied to all tags in this taglib class
//     * static encodeAsForTags = [someTag: 'html'] // default encodeAs for a single tag in this taglib class
//     * the codec setting accepts a string or a map. (explained in https://github.com/apache/grails-core/blob/scb-encoding-support/grails-web/src/main/groovy/org/codehaus/groovy/grails/web/util/WithCodecHelper.groovy#L54)
//     */
//    void "tag call as function call should use defaultEncodeAs / encodeAsForTags settings"() {
//    }

//    void "scriptlets should apply outCodec"() {
//        // Behaviour is inconsistent. <% ... %> and <%= ... %> do not apply default codec. ${g.xxx([:])} does apply codec. <g:xxx/> does not apply codec.
//        // Change <% ... %> and <%= ... %> to apply current default codec, as currently this is a little known security hole.
//    }

//    void "double encoding should be prevented"() {
//        // There is a risk of double-encoding of data when the developer is not aware of encodings already applied.
//    }
//
//    void "Plugins cannot have their pages break because the app developer changes default codec setting."() {
//    }
//
//    void "Ideally the user should never need to explicitly think about codecs or calling them except in rare situations."() {
//    }
//
//    void "Add a function/tag to switch the current default codec - effectively pushing and popping a default codec stack. This could take the form of a withCodec(name, Closure) method in tags."() {
//    }
//
//    void "Use this function/tag in core tags like <g:javascript> and <r:script> to automatically set an appropriate codec"() {
//    }
//
//    void "<g:render> and similar tags would need to set default codec to HTML again when including another GSP, pushing whatever was default onto a stack"() {
//    }

//    void "Add support for an optional encodeAs attribute to all tags automatically, such that the result will be encoded with that codec if specified i.e. var s = \${g.createLink(...., encodeAs:'JavaScript')}"() {
//    }

//    void "All GSPs in app or plugins default to HTML codec unless developer does something to change that using directive/tag"() {
//    }
//
//    void "All outputs of expressions/inline code apply the current default codec"() {
//    }
//
//    void "Tags are responsible for the correct encoding of their output, unless specified in encodeAs= attribute"() {
//    }
//
//    void "It's possible to use raw codec to mark some output as something that shouldn't be escaped"() {
//    }
//
//    void "support map argument to encodeAs attribute so that templateCodec, pageCodec & defaultCode can be changed separately"() {
//    }
}
