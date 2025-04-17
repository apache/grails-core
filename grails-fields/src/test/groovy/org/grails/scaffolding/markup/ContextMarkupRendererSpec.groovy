package org.grails.scaffolding.markup

import org.grails.scaffolding.ClosureCapture
import org.grails.scaffolding.ClosureCaptureSpecification
import org.grails.scaffolding.model.property.DomainProperty
import org.grails.datastore.mapping.model.PersistentEntity
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Subject
import spock.lang.Specification

@Subject(ContextMarkupRendererImpl)
class ContextMarkupRendererSpec extends ClosureCaptureSpecification {

    @Shared
    ContextMarkupRendererImpl renderer

    void setup() {
        renderer = new ContextMarkupRendererImpl()
    }

    @Ignore
    void "test listOutputContext"() {
        given:
        DomainProperty prop1 = Mock(DomainProperty) {
            1 * getDefaultLabel() >> "Prop 1"
            1 * getName() >> "prop1"
        }
        DomainProperty prop2 = Mock(DomainProperty) {
            1 * getDefaultLabel() >> "Prop 2"
            1 * getName() >> "prop2"
        }
        DomainProperty prop3 = Mock(DomainProperty) {
            1 * getDefaultLabel() >> "Prop 3"
            1 * getName() >> "prop3"
        }

        when:
        ClosureCapture closureCapture = getClosureCapture(renderer.listOutputContext(Mock(PersistentEntity), [prop1, prop2, prop3], { DomainProperty prop ->
            prop.name
        }))

        then:
        closureCapture.calls[0].name == "table"
        closureCapture.calls[0][0].name == "thead"
        closureCapture.calls[0][0][0].name == "tr"
        closureCapture.calls[0][0][0][0].name == "th"
        closureCapture.calls[0][0][0][0].args[0] == "Prop 1"
        closureCapture.calls[0][0][0][1].name == "th"
        closureCapture.calls[0][0][0][1].args[0] == "Prop 2"
        closureCapture.calls[0][0][0][2].name == "th"
        closureCapture.calls[0][0][0][2].args[0] == "Prop 3"
        closureCapture.calls[0][1].name == "tbody"
        closureCapture.calls[0][1][0].name == "tr"
        closureCapture.calls[0][1][0][0].name == "td"
        closureCapture.calls[0][1][0][0].args[0] == "prop1"
        closureCapture.calls[0][1][0][1].name == "td"
        closureCapture.calls[0][1][0][1].args[0] == "prop2"
        closureCapture.calls[0][1][0][2].name == "td"
        closureCapture.calls[0][1][0][2].args[0] == "prop3"
    }

    void "test inputContext (Domain)"() {
        when:
        ClosureCapture closureCapture = getClosureCapture(renderer.inputContext(Mock(PersistentEntity)) { ->
            span("foo")
        })

        then:
        closureCapture.calls[0].name == "fieldset"
        closureCapture.calls[0].args[0] == ["class": "form"]
        closureCapture.calls[0][0].name == "span"
        closureCapture.calls[0][0].args[0] == "foo"
    }

    @Ignore
    void "test inputContext (Property) required"() {
        given:
        DomainProperty property = Mock(DomainProperty) {
            2 * isRequired() >> true
            1 * getPathFromRoot() >> "bar"
            1 * getLabelKeys() >> null
            1 * getDefaultLabel() >> "Bar"
        }

        when:
        ClosureCapture closureCapture = getClosureCapture(renderer.inputContext(property) { ->
            input([type: "text"])
        })

        then:
        closureCapture.calls[0].name == "div"
        closureCapture.calls[0].args[0] == ["class": "fieldcontain required"]
        closureCapture.calls[0][0].name == "label"
        closureCapture.calls[0][0].args[0] == ["for": "bar"]
        closureCapture.calls[0][0].args[1] == "Bar"
        closureCapture.calls[0][0][0].name == "span"
        closureCapture.calls[0][0][0].args[0] == ["class": "required-indicator"]
        closureCapture.calls[0][0][0].args[1] == "*"
        closureCapture.calls[0][1].name == "input"
        closureCapture.calls[0][1].args[0] == ["type": "text"]
    }

    @Ignore
    void "test inputContext (Property) not required"() {
        given:
        DomainProperty property = Mock(DomainProperty) {
            2 * isRequired() >> false
            1 * getPathFromRoot() >> "bar"
            1 * getLabelKeys() >> null
            1 * getDefaultLabel() >> "Bar"
        }

        when:
        ClosureCapture closureCapture = getClosureCapture(renderer.inputContext(property) { ->
            input([type: "text"])
        })

        then:
        closureCapture.calls[0].name == "div"
        closureCapture.calls[0].args[0] == ["class": "fieldcontain"]
        closureCapture.calls[0][0].name == "label"
        closureCapture.calls[0][0].args[0] == ["for": "bar"]
        closureCapture.calls[0][0].args[1] == "Bar"
        closureCapture.calls[0][1].name == "input"
        closureCapture.calls[0][1].args[0] == ["type": "text"]
    }

    void "test outputContext (Domain)"() {
        given:
        PersistentEntity domain = Mock(PersistentEntity) {
            1 * getDecapitalizedName() >> "foo"
        }

        when:
        ClosureCapture closureCapture = getClosureCapture(renderer.outputContext(domain) { ->
            li("prop1")
            li("prop2")
            li("prop3")
        })

        then:
        closureCapture.calls[0].name == "ol"
        closureCapture.calls[0].args[0] == ["class": "property-list foo"]
        closureCapture.calls[0][0].name == "li"
        closureCapture.calls[0][0].args[0] == "prop1"
        closureCapture.calls[0][1].name == "li"
        closureCapture.calls[0][1].args[0] == "prop2"
        closureCapture.calls[0][2].name == "li"
        closureCapture.calls[0][2].args[0] == "prop3"
    }

    @Ignore
    void "test outputContext (Property)"() {
        given:
        DomainProperty property = Mock(DomainProperty) {
            2 * getPathFromRoot() >> "bar"
            1 * getLabelKeys() >> null
            1 * getDefaultLabel() >> "Bar"
        }

        when:
        ClosureCapture closureCapture = getClosureCapture(renderer.outputContext(property) {  ->
            span("x")
        })

        then:
        closureCapture.calls[0].name == "li"
        closureCapture.calls[0].args[0] == ["class": "fieldcontain"]
        closureCapture.calls[0][0].name == "span"
        closureCapture.calls[0][0].args[0] == ["id": "bar-label", class: "property-label"]
        closureCapture.calls[0][0].args[1] == "Bar"
        closureCapture.calls[0][1].name == "div"
        closureCapture.calls[0][1].args[0] == ["class": "property-value", "aria-labelledby": "bar-label"]
        closureCapture.calls[0][1][0].name == "span"
        closureCapture.calls[0][1][0].args[0] == "x"
    }

    @Ignore
    void "test embeddedInputContext"() {
        given:
        DomainProperty property = Mock(DomainProperty) {
            1 * getType() >> TimeZone
            1 * getLabelKeys() >> null
            1 * getDefaultLabel() >> "Bar"
        }

        when:
        ClosureCapture closureCapture = getClosureCapture(renderer.embeddedInputContext(property) {  ->
            span("x")
        })

        then:
        closureCapture.calls[0].name == "fieldset"
        closureCapture.calls[0].args[0] == [class: "embedded timeZone"]
        closureCapture.calls[0][0].name == "legend"
        closureCapture.calls[0][0].args[0] == "Bar"
        closureCapture.calls[0][1].name == "span"
        closureCapture.calls[0][1].args[0] == "x"
    }

    @Ignore
    void "test embeddedOutputContext"() {
        given:
        DomainProperty property = Mock(DomainProperty) {
            1 * getType() >> TimeZone
            1 * getLabelKeys() >> null
            1 * getDefaultLabel() >> "Bar"
        }

        when:
        ClosureCapture closureCapture = getClosureCapture(renderer.embeddedOutputContext(property) {  ->
            span("x")
        })

        then:
        closureCapture.calls[0].name == "fieldset"
        closureCapture.calls[0].args[0] == [class: "embedded timeZone"]
        closureCapture.calls[0][0].name == "legend"
        closureCapture.calls[0][0].args[0] == "Bar"
        closureCapture.calls[0][1].name == "span"
        closureCapture.calls[0][1].args[0] == "x"
    }
}
