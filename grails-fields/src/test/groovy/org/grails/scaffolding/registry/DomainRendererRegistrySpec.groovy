package org.grails.scaffolding.registry

import org.grails.scaffolding.model.property.DomainProperty
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by Jim on 5/26/2016.
 */
class DomainRendererRegistrySpec extends Specification {

    @Shared
    DomainOutputRendererRegistry registry

    void setup() {
        registry = new DomainOutputRendererRegistry()
    }

    void "test renderers are returned in order"() {
        given:
        DomainOutputRenderer levelOne = Stub(DomainOutputRenderer) {
            supports(_ as DomainProperty) >> true
        }
        DomainOutputRenderer levelTwo = Stub(DomainOutputRenderer) {
            supports(_ as DomainProperty) >> true
        }
        registry.registerDomainRenderer(levelOne, 1)
        registry.registerDomainRenderer(levelTwo, 2)

        when:
        DomainOutputRenderer resolved = registry.get(Mock(DomainProperty))

        then:
        resolved == levelTwo
    }

    void "test the last renderer added will have priority over others with the same priority"() {
        given:
        DomainOutputRenderer levelOne = Stub(DomainOutputRenderer) {
            supports(_ as DomainProperty) >> true
        }
        DomainOutputRenderer levelTwo = Stub(DomainOutputRenderer) {
            supports(_ as DomainProperty) >> true
        }
        registry.registerDomainRenderer(levelOne, 1)
        registry.registerDomainRenderer(levelTwo, 1)

        when:
        DomainOutputRenderer resolved = registry.get(Mock(DomainProperty))

        then:
        resolved == levelTwo
    }

    void "test only supported renderers are resolved"() {
        given:
        DomainOutputRenderer levelOne = Stub(DomainOutputRenderer) {
            supports(_ as DomainProperty) >> true
        }
        DomainOutputRenderer levelTwo = Stub(DomainOutputRenderer) {
            supports(_ as DomainProperty) >> false
        }
        registry.registerDomainRenderer(levelOne, 1)
        registry.registerDomainRenderer(levelTwo, 2)

        when:
        DomainOutputRenderer resolved = registry.get(Mock(DomainProperty))

        then:
        resolved == levelOne
    }
}
