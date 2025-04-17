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
@Subject(NumberInputRenderer)
class NumberInputRendererSpec extends ClosureCaptureSpecification {

    @Shared
    NumberInputRenderer renderer

    void setup() {
        renderer = new NumberInputRenderer()
    }

    void "test supports"() {
        given:
        DomainProperty property

        when:
        property = Mock(DomainProperty) {
            1 * getType() >> type
        }

        then:
        renderer.supports(property)

        where:
        type | _
        int  | _
        long  | _
        double  | _
        Integer  | _
        Long  | _
        Double  | _
    }

    void "test render"() {
        given:
        DomainProperty property
        ClosureCapture closureCapture

        when:
        property = Mock(DomainProperty) {
            1 * getConstrained() >> Mock(Constrained) {
                1 * getRange() >> (1..5)
            }
        }
        closureCapture = getClosureCapture(renderer.renderInput([:], property))

        then:
        closureCapture.calls[0].name == "input"
        closureCapture.calls[0].args[0] == ["type": "range", "min": 1, "max": 5]

        when:
        property = Mock(DomainProperty) {
            1 * getType() >> Integer
            1 * getConstrained() >> Mock(Constrained) {
                1 * getRange() >> null
                1 * getScale() >> null
                1 * getMin() >> null
                1 * getMax() >> null
            }
        }
        closureCapture = getClosureCapture(renderer.renderInput([:], property))

        then:
        closureCapture.calls[0].name == "input"
        closureCapture.calls[0].args[0] == ["type": "number"]

        when:
        property = Mock(DomainProperty) {
            1 * getType() >> Double
            1 * getConstrained() >> Mock(Constrained) {
                1 * getRange() >> null
                1 * getScale() >> null
                1 * getMin() >> null
                1 * getMax() >> null
            }
        }
        closureCapture = getClosureCapture(renderer.renderInput([:], property))

        then:
        closureCapture.calls[0].name == "input"
        closureCapture.calls[0].args[0] == ["type": "number", "step": "any"]

        when:
        property = Mock(DomainProperty) {
            1 * getType() >> Integer
            1 * getConstrained() >> Mock(Constrained) {
                1 * getRange() >> null
                2 * getScale() >> 3
                1 * getMin() >> null
                1 * getMax() >> null
            }
        }
        closureCapture = getClosureCapture(renderer.renderInput([:], property))

        then:
        closureCapture.calls[0].name == "input"
        closureCapture.calls[0].args[0] == ["type": "number", "step": "0.001"]

        when:
        property = Mock(DomainProperty) {
            1 * getType() >> Integer
            1 * getConstrained() >> Mock(Constrained) {
                1 * getRange() >> null
                1 * getScale() >> null
                2 * getMin() >> 5
                2 * getMax() >> 6
            }
        }
        closureCapture = getClosureCapture(renderer.renderInput([:], property))

        then:
        closureCapture.calls[0].name == "input"
        closureCapture.calls[0].args[0] == ["type": "number", "min": 5, "max": 6]

    }
}
