package org.grails.scaffolding.markup

import grails.persistence.Entity
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.scaffolding.model.DomainModelService
import org.grails.scaffolding.model.DomainModelServiceImpl
import org.grails.scaffolding.model.MocksDomain
import org.grails.scaffolding.model.property.DomainProperty
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Embedded
import org.grails.scaffolding.model.property.DomainPropertyFactory
import org.grails.scaffolding.model.property.DomainPropertyFactoryImpl
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by Jim on 5/29/2016.
 */
class DomainMarkupRendererSpec extends Specification implements MocksDomain {

    @Shared
    DomainMarkupRendererImpl renderer

    void setup() {
        renderer = new DomainMarkupRendererImpl()
    }

    void "test renderListOutput"() {
        given:
        PersistentEntity persistentEntity = Mock(PersistentEntity)
        PersistentEntity embeddedEntity = Mock(PersistentEntity)
        DomainProperty prop1 = Mock(DomainProperty) {
            1 * getName() >> "prop1"
            1 * getPersistentProperty() >> Mock(PersistentProperty)
        }
        DomainProperty prop2 = Mock(DomainProperty) {
            1 * getName() >> "prop2"
            1 * getPersistentProperty() >> Mock(PersistentProperty)
        }
        DomainProperty prop3 = Mock(DomainProperty) {
            0 * getName() >> "prop3"
            2 * getPersistentProperty() >> Mock(Embedded) {
                1 * getAssociatedEntity() >> embeddedEntity
            }
        }
        DomainProperty prop4 = Mock(DomainProperty) {
            1 * getName() >> "prop4"
            1 * getPersistentProperty() >> Mock(PersistentProperty)
        }
        DomainProperty prop5 = Mock(DomainProperty) {
            1 * getName() >> "prop5"
            1 * getPersistentProperty() >> Mock(PersistentProperty)
        }
        DomainProperty prop6 = Mock(DomainProperty) {
            0 * getName() >> "prop6"
            1 * getPersistentProperty() >> Mock(PersistentProperty)
        }
        DomainProperty embeddedProp1 = Mock(DomainProperty) {
            1 * getName() >> "embeddedProp1"
            0 * getPersistentProperty()
        }
        DomainProperty embeddedProp2 = Mock(DomainProperty) {
            1 * getName() >> "embeddedProp2"
            0 * getPersistentProperty()
        }
        DomainProperty embeddedProp3 = Mock(DomainProperty) {
            1 * getName() >> "embeddedProp3"
            0 * getPersistentProperty()
        }
        List props = [prop1, prop2, prop3, prop4, prop5, prop6]
        List embeddedProps = [embeddedProp1, embeddedProp2, embeddedProp3]
        renderer.domainModelService = Mock(DomainModelService) {
            1 * getListOutputProperties(persistentEntity) >> props
            1 * getOutputProperties(embeddedEntity) >> embeddedProps
        }
        renderer.contextMarkupRenderer = Mock(ContextMarkupRenderer) {
            1 * listOutputContext(_ as PersistentEntity, [prop1, prop2, embeddedProp1, embeddedProp2, embeddedProp3, prop4, prop5], _ as Closure) >> { entity, properties, closure ->
                return { ->
                    properties.each { DomainProperty prop ->
                        div(closure.call(prop))
                    }
                }
            }
        }
        renderer.propertyMarkupRenderer = Mock(PropertyMarkupRenderer) {
            7 * renderListOutput(_ as DomainProperty) >> { DomainProperty prop ->
                return { -> span(prop.name) }
            }
        }

        when:
        String output = renderer.renderListOutput(persistentEntity)

        then:
        output == ["prop1", "prop2", "embeddedProp1", "embeddedProp2", "embeddedProp3", "prop4", "prop5"].collect {
            "<div>\n  <span>$it</span>\n</div>"
        }.join("\n")
    }

    void "test renderListOutput (real domain)"() {
        given:
        MappingContext mappingContext = new KeyValueMappingContext("test")
        PersistentEntity persistentEntity = mockDomainClass(mappingContext, MainDomain)
        //mockDomainClass(mappingContext, EmbeddedDomain)
        DomainPropertyFactory domainPropertyFactory = new DomainPropertyFactoryImpl(convertEmptyStringsToNull: true, trimStrings: true, grailsDomainClassMappingContext: mappingContext)
        renderer.domainModelService = new DomainModelServiceImpl(domainPropertyFactory: domainPropertyFactory)
        renderer.contextMarkupRenderer = Mock(ContextMarkupRenderer) {
            1 * listOutputContext(_ as PersistentEntity, _ as List, _ as Closure) >> { entity, properties, closure ->
                return { ->
                    properties.each { DomainProperty prop ->
                        div(closure.call(prop))
                    }
                }
            }
        }
        renderer.propertyMarkupRenderer = Mock(PropertyMarkupRenderer) {
            7 * renderListOutput(_ as DomainProperty) >> { DomainProperty prop ->
                return { -> span(prop.name) }
            }
        }

        when:
        String output = renderer.renderListOutput(persistentEntity)

        then:
        output == ["id", "prop1", "prop2", "embeddedProp1", "embeddedProp2", "embeddedProp3", "prop4"].collect {
            "<div>\n  <span>$it</span>\n</div>"
        }.join("\n")
    }

    void "test renderForm"() {
        given:
        PersistentEntity domain = Mock(PersistentEntity)
        PersistentEntity embedded = Mock(PersistentEntity)
        DomainProperty prop1 = Mock(DomainProperty) {
            1 * getName() >> "prop1"
            1 * getPersistentProperty() >> Mock(PersistentProperty)
        }
        DomainProperty prop2 = Mock(DomainProperty) {
            2 * getPersistentProperty() >> Mock(Embedded) {
                1 * getAssociatedEntity() >> embedded
            }
        }
        DomainProperty prop3 = Mock(DomainProperty) {
            1 * getName() >> "prop3"
        }
        renderer.domainModelService = Mock(DomainModelService) {
            1 * getInputProperties(domain) >> [prop1, prop2]
            1 * getInputProperties(embedded) >> [prop3]
        }
        renderer.contextMarkupRenderer = Mock(ContextMarkupRenderer) {
            2 * inputContext(_ as DomainProperty, _ as Closure) >> { DomainProperty prop, Closure c ->
                return { ->
                    div(c)
                }
            }
            1 * inputContext(_ as PersistentEntity, _ as Closure) >> { PersistentEntity d, Closure c ->
                return { ->
                    form(c)
                }
            }
            1 * embeddedInputContext(_ as DomainProperty, _ as Closure) >> { DomainProperty prop, Closure c ->
                return { ->
                    fieldset(c)
                }
            }
        }
        renderer.propertyMarkupRenderer = Mock(PropertyMarkupRenderer) {
            2 * renderInput(_ as DomainProperty) >> { DomainProperty prop ->
                return { -> span(prop.name) }
            }
        }

        when:
        String output = renderer.renderInput(domain)

        then:
        output == "<form>\n  <div>\n    <span>prop1</span>\n  </div>\n  <fieldset>\n    <div>\n      <span>prop3</span>\n    </div>\n  </fieldset>\n</form>"
    }

    void "test renderOutput"() {
        given:
        PersistentEntity domain = Mock(PersistentEntity)
        PersistentEntity embedded = Mock(PersistentEntity)
        DomainProperty prop1 = Mock(DomainProperty) {
            1 * getName() >> "prop1"
            1 * getPersistentProperty() >> Mock(PersistentProperty)
        }
        DomainProperty prop2 = Mock(DomainProperty) {
            2 * getPersistentProperty() >> Mock(Embedded) {
                1 * getAssociatedEntity() >> embedded
            }
        }
        DomainProperty prop3 = Mock(DomainProperty) {
            1 * getName() >> "prop3"
        }
        renderer.domainModelService = Mock(DomainModelService) {
            1 * getOutputProperties(domain) >> [prop1, prop2]
            1 * getOutputProperties(embedded) >> [prop3]
        }
        renderer.contextMarkupRenderer = Mock(ContextMarkupRenderer) {
            2 * outputContext(_ as DomainProperty, _ as Closure) >> { DomainProperty prop, Closure c ->
                return { ->
                    div(c)
                }
            }
            1 * outputContext(_ as PersistentEntity, _ as Closure) >> { PersistentEntity d, Closure c ->
                return { ->
                    form(c)
                }
            }
            1 * embeddedOutputContext(_ as DomainProperty, _ as Closure) >> { DomainProperty prop, Closure c ->
                return { ->
                    fieldset(c)
                }
            }
        }
        renderer.propertyMarkupRenderer = Mock(PropertyMarkupRenderer) {
            2 * renderOutput(_ as DomainProperty) >> { DomainProperty prop ->
                return { -> span(prop.name) }
            }
        }

        when:
        String output = renderer.renderOutput(domain)

        then:
        output == "<form>\n  <div>\n    <span>prop1</span>\n  </div>\n  <fieldset>\n    <div>\n      <span>prop3</span>\n    </div>\n  </fieldset>\n</form>"
    }

    @Entity
    class MainDomain {
        String prop1
        String prop2
        EmbeddedDomain prop3
        String prop4
        String prop5
        String prop6

        static embedded = ['prop3']
        static constraints = {
            prop1(order: 1)
            prop2(order: 2)
            prop3(order: 3)
            prop4(order: 4)
            prop5(order: 5)
            prop6(order: 6)
        }
    }

    class EmbeddedDomain {
        String embeddedProp1
        String embeddedProp2
        String embeddedProp3
    }
}
