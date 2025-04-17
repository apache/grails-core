package grails.plugin.formfields

import grails.plugin.formfields.mock.Product
import grails.plugin.formfields.taglib.AbstractFormFieldsTagLibSpec
import grails.testing.web.taglib.TagLibUnitTest
import org.grails.datastore.mapping.model.MappingContext
import spock.lang.*

@Issue('https://github.com/grails-fields-plugin/grails-fields/issues/85')
class DerivedPropertySpec extends AbstractFormFieldsTagLibSpec  implements TagLibUnitTest<FormFieldsTagLib> {

    FormFieldsTemplateService mockFormFieldsTemplateService = Mock(FormFieldsTemplateService)
    Product productInstance

    def setupSpec() {
        mockDomain(Product)
    }

    def setup() {

        mockFormFieldsTemplateService.findTemplate(_, 'field') >> [path: '/_fields/default/field']
        tagLib.formFieldsTemplateService = mockFormFieldsTemplateService

        // @Mock isn't aware of formulae so we need to set this manually
        MappingContext mappingContext = dataStore.mappingContext
        mappingContext.getPersistentEntity(Product.name).getPropertyByName('tax').mapping.mappedForm.derived = true

        productInstance = new Product(name: 'MacBook Pro', netPrice: 1499, taxRate: 0.2).save(failOnError: true)
    }

    void 'derived properties are ignored by f:all'() {
        given:
        views["/_fields/default/_wrapper.gsp"] = '${property} '

        when:
        def output = applyTemplate('<f:all bean="productInstance"/>', [productInstance: productInstance])

        then:
        !(output =~ /\btax\b/)
    }

}
