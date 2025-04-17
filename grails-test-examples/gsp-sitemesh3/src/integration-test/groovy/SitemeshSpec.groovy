import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration

@Integration
class SitemeshSpec extends ContainerGebSpec {

    void "forced layout"() {
        when:
        browser.go 'demo/index'

        then:
        browser.driver.pageSource.contains('Do you like BootStrap?')
    }

    void "decorator chaining"() {
        when:
        browser.go 'demo/chaining'

        then:
        browser.driver.pageSource.contains('This is so cool.')
    }

    void "jsp demo"() {
        when:
        browser.go 'demo/jsp'

        then:
        def container = browser.$('div.container')
        container

        browser.driver.pageSource.contains('Hello World, I am a JSP page!')
    }

    void "text"() {
        when:
        browser.go 'demo/renderText'

        then:
        downloadText() == '''<p>Hello World</p>'''
    }

    void "Controller 500 Example"() {
        when:
        browser.go 'demo/exception'

        then:
        browser.driver.pageSource.contains('Whoops, why would you ever want to see an exception??')
    }

    void "View 500 Example"() {
        when:
        browser.go 'demo/viewException'

        then:
        browser.driver.pageSource.contains('Oh Man, this view sucks!')
    }

    void "404 Error"() {
        when:
        browser.go 'demo/404'

        then:
        browser.driver.pageSource.contains('Error: Page Not Found (404)')
    }
}
