package org.grails.scaffolding.registry.input

import org.grails.scaffolding.ClosureCapture
import org.grails.scaffolding.ClosureCaptureSpecification
import org.grails.scaffolding.model.property.DomainProperty
import org.grails.scaffolding.registry.DomainInputRenderer
import spock.lang.Shared
import spock.lang.Subject

/**
 * Created by Jim on 6/6/2016.
 */
@Subject(MapToSelectInputRenderer)
class MapToSelectInputRendererSpec extends ClosureCaptureSpecification {

    @Shared
    MapToSelectInputRenderer renderer

    void setup() {
        renderer = new Renderer()
    }

    void "test render"() {
        given:
        ClosureCapture closureCapture

        when:
        closureCapture = getClosureCapture(renderer.renderInput([:], Mock(DomainProperty)))

        then:
        closureCapture.calls[0].name == "select"
        closureCapture.calls[0].args[0] == [:]
        closureCapture.calls[0][0].name == "option"
        closureCapture.calls[0][0].args[0] == "A"
        closureCapture.calls[0][0].args[1] == ["value": "a"]
        closureCapture.calls[0][1].args[0] == "B"
        closureCapture.calls[0][1].args[1] == ["value": "b"]
        closureCapture.calls[0][2].args[0] == "Cat"
        closureCapture.calls[0][2].args[1] == ["value": "cat", "selected": ""]
    }


    class Renderer implements MapToSelectInputRenderer<String> {
        @Override
        String getOptionValue(String o) {
            o.capitalize()
        }

        @Override
        String getOptionKey(String o) {
            o.toLowerCase()
        }

        @Override
        String getDefaultOption() {
            "cat"
        }

        @Override
        Map<String, String> getOptions() {
            ["a": "A", "b": "B", "cat": "Cat"]
        }

        @Override
        boolean supports(DomainProperty property) {
            false
        }
    }

}
