package functionaltests


import grails.gorm.transactions.Rollback
import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration
import spock.lang.Issue

@Integration
@Rollback
class RedirectWithAndWithoutParamsFunctionalSpec extends ContainerGebSpec {

    @Issue('grails-core-#10965')
    void 'Params are not added to the url after a redirect even if they are passed to the redirect'() {
        when: 'creating and object'
        go "/baz/create"
        $('input', name:'name').value('foo')
        $('input', type:'submit').click()

        then: 'the params are not added to the url'
        !currentUrl.contains('?name=foo')
        driver.pageSource.contains('"id":')
        driver.pageSource.contains('"name":"foo"')
    }

    @Issue('grails-core-#10622')
    void 'Params are not on the url when redirecting from UrlMappings without enabling params redirect'() {
        when: 'redirecting from url mappings without enabling params redirect'
        go "/old-with-uri?name=foo"

        then: 'the params are not added to the url'
        !currentUrl.contains('/new-url?name=foo')

        and: 'the params are not on the output'
        !driver.pageSource.contains('"name":"foo"')

        when: 'redirecting from url mappings without enabling params redirect'
        go "/old-with-controller-action?name=foo"

        then: 'the params are not added to the url'
        !currentUrl.contains('/new-url?name=foo')

        and: 'the params are not on the output'
        !driver.pageSource.contains('"name":"foo"')
    }

    @Issue('grails-core-#10622')
    void 'Params are not on the url when redirecting from UrlMappings with disabling params redirect'() {
        when: 'redirecting from url mappings without enabling params redirect'
        go "/old-with-uri-2?name=foo"

        then: 'the params are not added to the url'
        !currentUrl.contains('/new-url?name=foo')

        and: 'the params are not on the output'
        !driver.pageSource.contains('"name":"foo"')

        when: 'redirecting from url mappings without enabling params redirect'
        go "/old-with-controller-action-2?name=foo"

        then: 'the params are not added to the url'
        !currentUrl.contains('/new-url?name=foo')

        and: 'the params are not on the output'
        !driver.pageSource.contains('"name":"foo"')
    }

    @Issue('grails-core-#10622')
    void 'Params are on the url when redirecting from UrlMappings enabling params redirect'() {
        when: 'redirecting from url mappings without enabling params redirect'
        go "/old-controller-action-with-params?name=foo"

        then: 'the params are added to the url'
        currentUrl.contains('/new-url?name=foo')

        and: 'the params are on the output'
        driver.pageSource.contains('"name":"foo"')
    }
}
