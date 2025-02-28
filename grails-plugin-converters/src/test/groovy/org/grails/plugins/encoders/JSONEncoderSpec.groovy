package org.grails.plugins.encoders

import grails.encoders.JSONEncoder
import spock.lang.Issue
import spock.lang.Specification

class JSONEncoderSpec extends Specification {

    @Issue("GRAILS-11513")
    def "should properly escape quotes when using encodeToWriter method of JSONEncoder"() {
        given:
        char[] inputBuf = input.toCharArray()
        JSONEncoder encoder = new JSONEncoder()
        StringWriter writerStrings = new StringWriter()
        StringWriter writerCharArrays = new StringWriter()
        when:
        encoder.encodeToWriter(input, 0, input.length(), writerStrings, null)
        encoder.encodeToWriter(inputBuf, 0, inputBuf.length, writerCharArrays, null)
        then:
        writerStrings.toString() == result
        writerCharArrays.toString() == result
        where:
        input | result
        "I contain a TAB \u000B" | "I contain a TAB \\u000B"
        "I contain a \"Quote\"!" | 'I contain a \\"Quote\\"!'
        "\"Quote\"" | '\\"Quote\\"'
        "\"Quote\"-" | '\\"Quote\\"-'
        "-\"Quote\"" | '-\\"Quote\\"'
        "-\"Quote\"-" | '-\\"Quote\\"-'
        "\"" | '\\"'
        "\"\"" | '\\"\\"'
        "\"Q\"" | '\\"Q\\"'
        "\"Q" | '\\"Q'
        "Q\"" | 'Q\\"'
    }
}
