package org.grails.scaffolding.registry.input

import org.grails.scaffolding.ClosureCapture
import org.grails.scaffolding.ClosureCaptureSpecification
import org.grails.scaffolding.model.property.DomainProperty
import spock.lang.Shared
import spock.lang.Subject

@Subject(DateInputRenderer)
class DateInputRendererSpec extends ClosureCaptureSpecification {

    @Shared
    DateInputRenderer renderer

    void setup() {
        renderer = new DateInputRenderer()
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
        Date | _
        Calendar | _
        java.sql.Date | _
    }

    void "test render"() {
        when:
        ClosureCapture closureCapture = getClosureCapture(renderer.renderInput([:], Mock(DomainProperty)))

        then:
        closureCapture.calls[0].name == "input"
        closureCapture.calls[0].args[0] == ["type": "date", "placeholder": "YYYY-MM-DD"]
    }
}
