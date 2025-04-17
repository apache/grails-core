package org.grails.scaffolding.markup

import org.grails.scaffolding.model.property.Constrained
import org.grails.scaffolding.model.property.DomainProperty
import org.grails.scaffolding.registry.DomainInputRenderer
import org.grails.scaffolding.registry.DomainInputRendererRegistry
import org.grails.scaffolding.registry.DomainOutputRenderer
import org.grails.scaffolding.registry.DomainOutputRendererRegistry
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

/**
 * Created by Jim on 6/7/2016.
 */
@Subject(PropertyMarkupRendererImpl)
class PropertyMarkupRendererSpec extends Specification {

    @Shared
    PropertyMarkupRendererImpl renderer

    void setup() {
        renderer = new PropertyMarkupRendererImpl()
    }

    void "test renderListOutput"() {
        given:
        renderer.domainOutputRendererRegistry = Mock(DomainOutputRendererRegistry)
        DomainProperty property = Mock(DomainProperty)

        when:
        renderer.renderListOutput(property)

        then:
        1 * renderer.domainOutputRendererRegistry.get(property) >> Mock(DomainOutputRenderer) {
            1 * renderListOutput(property)
        }
    }

    void "test renderOutput"() {
        given:
        renderer.domainOutputRendererRegistry = Mock(DomainOutputRendererRegistry)
        DomainProperty property = Mock(DomainProperty)

        when:
        renderer.renderOutput(property)

        then:
        1 * renderer.domainOutputRendererRegistry.get(property) >> Mock(DomainOutputRenderer) {
            1 * renderOutput(property)
        }
    }

    void "test renderInput"() {
        given:
        renderer.domainInputRendererRegistry = Mock(DomainInputRendererRegistry)
        DomainProperty property = Mock(DomainProperty) {
            1 * getPathFromRoot() >> "city"
            1 * isRequired() >> false
            1 * getConstrained() >> null
        }

        when:
        renderer.renderInput(property)

        then:
        1 * renderer.domainInputRendererRegistry.get(property) >> Mock(DomainInputRenderer) {
            1 * renderInput([name: "city", id: "city"], property)
        }
    }

    void "test getStandardAttributes"() {
        given:
        DomainProperty property = Mock(DomainProperty) {
            1 * getPathFromRoot() >> "city"
            1 * isRequired() >> false
            1 * getConstrained() >> null
        }

        when:
        Map attrs = renderer.getStandardAttributes(property)

        then:
        attrs == [name: "city", id: "city"]
    }

    void "test getStandardAttributes required property"() {
        given:
        DomainProperty property = Mock(DomainProperty) {
            1 * getPathFromRoot() >> "city"
            1 * isRequired() >> true
            1 * getConstrained() >> null
        }

        when:
        Map attrs = renderer.getStandardAttributes(property)

        then:
        attrs == [name: "city", id: "city", required: null]
    }

    void "test getStandardAttributes readonly property"() {
        given:
        DomainProperty property = Mock(DomainProperty) {
            1 * getPathFromRoot() >> "city"
            1 * isRequired() >> false
            2 * getConstrained() >> Mock(Constrained) {
                1 * isEditable() >> false
            }
        }

        when:
        Map attrs = renderer.getStandardAttributes(property)

        then:
        attrs == [name: "city", id: "city", readonly: null]
    }
}
