package functional.tests

import grails.testing.mixin.integration.Integration
import grails.testing.spock.RunOnce
import groovy.xml.XmlSlurper
import groovy.xml.slurpersupport.GPathResult
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import org.junit.jupiter.api.BeforeEach

@Integration(applicationClass = Application)
class TestGmlControllerSpec extends HttpClientSpec {

    @RunOnce
    @BeforeEach
    void init() {
        super.init()
    }

    void "Test GML response from action that returns a model"() {
        when:"When an action that renders a GML view is requested"
        String uri = '/testGml/testView'
        HttpRequest request = HttpRequest.GET(uri)
        HttpResponse<String> rsp = client.toBlocking().exchange(request, String)
        GPathResult content = new XmlSlurper().parseText(rsp.body())

        then:"The XML view is rendered"
        content.car.size() == 1
        content.car[0].@make.text() == "Audi"
    }
}
