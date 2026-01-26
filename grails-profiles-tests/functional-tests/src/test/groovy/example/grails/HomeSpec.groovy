package example.grails

import geb.Browser
import geb.spock.GebSpec
import spock.lang.Requires

class HomeSpec extends GebSpec {

    //@Requires({ Utils.available('http://localhost:8080') && Utils.available('http://localhost:3000') })
    def "homage shows a list of available controllers"() {
        given:
        Browser browser = new Browser()
        List<String> expectedControllerNames = ['demo.ApplicationController']

        when:
        HomePage homePage = browser.to(HomePage)
        List<String> controllerNames = homePage.controllerNames()

        then:
        expectedControllerNames == controllerNames
    }
}
