package org.grails.web.taglib

import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals

class MessageTagTests extends AbstractGrailsTagTests {

    @Test
    void testMessageTagInTemplate() {
        def template = '<g:message code="test.code" />'
        messageSource.addMessage("test.code", new Locale("en"), "hello world!")

        assertOutputEquals 'hello world!', template
    }

    @Test
    void testMessageTag() {
        StringWriter sw = new StringWriter();

        withTag("message", sw) { tag ->

            // test when no message found it returns code
            def attrs = [code:"test.code"]
            def result=tag.call(attrs)
            assertEquals "test.code", result

            // now test that when there is a message it finds it
            messageSource.addMessage("test.code", new Locale("en"), "hello world!")
            result = tag.call(attrs)
            assertEquals "hello world!", result

            // now test with arguments
            messageSource.addMessage("test.args", new Locale("en"), "hello {0}!")
            attrs = [code:"test.args", args:["fred"]]

            result = tag.call(attrs)

            assertEquals "hello fred!", result
        }
    }

    @Test
    void testMessageTagWithCodec() {
        StringWriter sw = new StringWriter();

        withTag("message", sw) { tag ->

            def attrs = [code:"test.code", encodeAs:'HTML']
            messageSource.addMessage("test.code", new Locale("en"), ">>&&")
            def result = tag.call(attrs)
            assertEquals "&gt;&gt;&amp;&amp;", result
        }
    }
}
