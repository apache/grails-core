package functionaltests


import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration
import spock.lang.Issue
import spock.lang.PendingFeature

@Integration(applicationClass = Application)
class InterceptorFunctionalSpec extends ContainerGebSpec {

    @Issue('grails/grails-core#9434')
    @PendingFeature(reason = 'text does not match')
    void "Test that an interceptor exception is handled correctly"() {
        when:
        go '/errors/throwErrorInInterceptor'

        then:
        $().text() == 'Message = Interceptor threw error'
    }

    @Issue('grails/grails-core#9183')
    void "Test that an after interceptor can render text and return false to disable view rendering"() {
        when:
        go '/demo/show'

        then:
        $().text() == 'the after interceptor rendered this'
    }

    void 'Test that after interceptor can render a model and view'() {
        when:
        go '/demo/show?interceptorRendersView=true'

        then:
        $().text() == 'Name: JSB'
    }

    void 'Test that before interceptor can render a model and view'() {
        when:
        go '/demo/another'

        then:
        $().text() == 'Name: JSB'
    }    

    void 'Test that after interceptor can render text'() {
        when:
        go '/demo/show?interceptorRendersText=true'

        then:
        $().text() == 'text rendered by interceptor'
    }

    @Issue('grails/grails-core#9194')
    void 'Test that after interceptor can redirect'() {
        when:
        go '/demo/show?interceptorRedirects=true'

        then:
        $().text() == 'Hi There! Special Action: redirect'
    }


    @Issue('grails/grails-core#9194')
    void 'Test that after interceptor can forward'() {
        when:
        go '/demo/show?interceptorForwards=true'

        then:
        $().text() == 'Hi There! Special Action: forward'
    }

    @Issue('grails/grails-core#9194')
    void 'Test that after interceptor can chain'() {
        when:
        go '/demo/show?interceptorChains=true'

        then:
        $().text() == 'Hi There! Special Action: chain'
    }
}
