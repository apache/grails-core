package org.grails.scaffolding.registry.input

import org.grails.scaffolding.ClosureCapture
import org.grails.scaffolding.ClosureCaptureSpecification
import org.grails.scaffolding.model.property.DomainProperty
import grails.web.mapping.LinkGenerator
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.ToMany
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Subject

@Subject(BidirectionalToManyInputRenderer)
class BidirectionalToManyInputRendererSpec extends ClosureCaptureSpecification {

    @Shared
    BidirectionalToManyInputRenderer renderer

    void setup() {
        renderer = new BidirectionalToManyInputRenderer(Mock(LinkGenerator))
    }

    void "test supports"() {
        given:
        DomainProperty property

        when:
        property = Mock(DomainProperty) {
            1 * getPersistentProperty() >> Mock(ToMany) {
                1 * isBidirectional() >> true
            }
        }

        then:
        renderer.supports(property)
    }

    @Ignore
    void "test render"() {
        given:
        DomainProperty property
        renderer.linkGenerator = Mock(LinkGenerator) {
            1 * link([resource: Calendar, action: "create", params: ["timeZone.id": ""]]) >> "http://www.google.com"
        }

        when:
        property = Mock(DomainProperty) {
            1 * getRootBeanType() >> TimeZone
            2 * getAssociatedType() >> Calendar
        }
        ClosureCapture closureCapture = getClosureCapture(renderer.renderInput([required: "", readonly: ""], property))

        then:
        closureCapture.calls[0].name == "a"
        closureCapture.calls[0].args[0] == "Add Calendar"
        closureCapture.calls[0].args[1] == [href: "http://www.google.com"]
    }
}
