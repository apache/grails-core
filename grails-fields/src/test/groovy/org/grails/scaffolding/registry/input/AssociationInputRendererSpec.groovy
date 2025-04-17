package org.grails.scaffolding.registry.input

import org.grails.scaffolding.ClosureCaptureSpecification
import org.grails.scaffolding.model.property.DomainProperty
import org.grails.datastore.mapping.model.types.ToOne
import spock.lang.Shared
import spock.lang.Subject

/**
 * Created by Jim on 6/7/2016.
 */
@Subject(AssociationInputRenderer)
class AssociationInputRendererSpec extends ClosureCaptureSpecification {

    @Shared
    AssociationInputRenderer renderer

    void setup() {
        renderer = new AssociationInputRenderer()
    }

    void "test supports"() {
        when:
        DomainProperty property = Mock(DomainProperty) {
            1 * getPersistentProperty() >> Mock(ToOne)
        }

        then:
        renderer.supports(property)
    }
}
