package org.grails.encoder

import org.grails.encoder.impl.HTMLEncoder
import org.grails.encoder.impl.NoneEncoder
import spock.lang.Issue
import spock.lang.Specification

class DefaultEncodingStateRegistrySpec extends Specification {

    @Issue("https://github.com/apache/grails-core/issues/11488")
    void "should not have previous encoding state"() {
        given:
        DefaultEncodingStateRegistry encodingStateRegistry = new DefaultEncodingStateRegistry()
        HTMLEncoder htmlEncoder = new HTMLEncoder()

        expect:
        for (int i = 0; i < 100000; i++) {
            String value = "value_&${UUID.randomUUID()}"
            EncodingState encodingState = encodingStateRegistry.getEncodingStateFor(value)
            assert encodingState == EncodingStateImpl.UNDEFINED_ENCODING_STATE
            encodingStateRegistry.registerEncodedWith(htmlEncoder, value)
        }
    }

    void "encoding should be specific to a given instance in the encoding state registry"() {
        given:
        DefaultEncodingStateRegistry encodingStateRegistry = new DefaultEncodingStateRegistry()
        HTMLEncoder htmlEncoder = new HTMLEncoder()
        NoneEncoder noneEncoder = new NoneEncoder()
        def string1 = htmlEncoder.encode('Hello world & hi')
        def string2 = 'Hello world &amp; hi'
        assert string1 == string2

        when:
        encodingStateRegistry.registerEncodedWith(htmlEncoder, string1)
        encodingStateRegistry.registerEncodedWith(noneEncoder, string2)

        then:
        encodingStateRegistry.getEncodingStateFor(string1).getEncoders() == [htmlEncoder] as Set
        encodingStateRegistry.getEncodingStateFor(string2).getEncoders() == [noneEncoder] as Set
    }
}
