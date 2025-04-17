package org.grails.scaffolding.registry.input

import org.grails.scaffolding.ClosureCapture
import org.grails.scaffolding.ClosureCaptureSpecification
import org.grails.scaffolding.model.property.DomainProperty
import org.grails.scaffolding.model.property.Constrained
import spock.lang.Shared
import spock.lang.Subject

/**
 * Created by Jim on 6/6/2016.
 */
@Subject(InListInputRenderer)
class InListInputRendererSpec extends ClosureCaptureSpecification {

    @Shared
    InListInputRenderer renderer

    void setup() {
        renderer = new InListInputRenderer()
    }

    void "test supports"() {
        given:
        DomainProperty property

        when:
        property = Mock(DomainProperty) {
            1 * getConstrained() >> Mock(Constrained) {
                1 * getInList() >> [1]
            }
        }

        then:
        renderer.supports(property)
    }

    void "test render"() {
        given:
        DomainProperty property
        ClosureCapture closureCapture

        when:
        property = Mock(DomainProperty) {
            1 * getConstrained() >> Mock(Constrained) {
                1 * getInList() >> [1, 2]
            }
        }
        closureCapture = getClosureCapture(renderer.renderInput([:], property))

        then:
        closureCapture.calls[0].name == "select"
        closureCapture.calls[0].args[0] == [:]
        closureCapture.calls[0][0].name == "option"
        closureCapture.calls[0][0].args[0] == 1
        closureCapture.calls[0][0].args[1] == ["value": 1]
        closureCapture.calls[0][1].name == "option"
        closureCapture.calls[0][1].args[0] == 2
        closureCapture.calls[0][1].args[1] == ["value": 2]
    }
}
