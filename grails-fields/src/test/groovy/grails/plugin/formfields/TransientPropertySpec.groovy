package grails.plugin.formfields

import grails.plugin.formfields.taglib.AbstractFormFieldsTagLibSpec
import grails.testing.web.taglib.TagLibUnitTest
import spock.lang.Issue
import grails.plugin.formfields.mock.User

@Issue('https://github.com/grails-fields-plugin/grails-fields/issues/87')
class TransientPropertySpec extends AbstractFormFieldsTagLibSpec implements TagLibUnitTest<FormFieldsTagLib> {

    FormFieldsTemplateService mockFormFieldsTemplateService = Mock(FormFieldsTemplateService)
    User userInstance

    def setupSpec() {
        mockDomain(User)
    }

    def setup() {
        mockFormFieldsTemplateService.findTemplate(_, 'wrapper', null, null) >> [path: '/_fields/default/wrapper']
        mockFormFieldsTemplateService.getTemplateFor('wrapper') >> "wrapper"
        tagLib.formFieldsTemplateService = mockFormFieldsTemplateService

        userInstance = new User(email: 'rob@freeside.co', password: 'yuonocanhaz', confirmPassword: 'yuonocanhaz').save(failOnError: true)
    }

    void 'transient properties can be rendered by f:field'() {
        given:
        views["/_fields/default/_wrapper.gsp"] = '${value}'

        when:
        def output = applyTemplate('<f:field bean="userInstance" property="confirmPassword"/>', [userInstance: userInstance])

        then:
        output == userInstance.confirmPassword
    }
}
