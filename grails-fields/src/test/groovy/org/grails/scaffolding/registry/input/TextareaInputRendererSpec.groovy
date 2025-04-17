package org.grails.scaffolding.registry.input

import org.grails.scaffolding.ClosureCapture
import org.grails.scaffolding.ClosureCaptureSpecification
import org.grails.scaffolding.model.property.DomainProperty
import org.grails.scaffolding.model.property.Constrained
import spock.lang.Shared
import spock.lang.Subject

@Subject(TextareaInputRenderer)
class TextareaInputRendererSpec extends ClosureCaptureSpecification {


    @Shared
    TextareaInputRenderer renderer

    void setup() {
        renderer = new TextareaInputRenderer()
    }

    void "test supports"() {
        given:
        DomainProperty prop = Mock(DomainProperty) {
            1 * getConstrained() >> Mock(Constrained) {
                1 * getWidget() >> "textarea"
            }
        }

        expect:
        renderer.supports(prop)
    }

    void "test render"() {
        given:
        DomainProperty property = Mock(DomainProperty) {
            1 * getConstrained() >> Mock(Constrained) {
                1 * getMaxSize() >> 20
            }
        }

        when:
        ClosureCapture closureCapture = getClosureCapture(renderer.renderInput([:], property))

        then:
        closureCapture.calls[0].name == "textarea"
        closureCapture.calls[0].args[0] == ["maxlength": 20]
    }
}
