package functionaltests

import grails.testing.mixin.integration.Integration
import groovy.json.JsonSlurper
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import spock.lang.Specification

@Integration(applicationClass = Application)
class BookRestfulControllerSpec extends Specification {

    def "A RestfulController exposes an index endpoint for a domain class"() {
        given:
        String baseUrl = "http://localhost:$serverPort"
        HttpClient client  = HttpClient.create(new URL(baseUrl))

        when:
        HttpResponse<String> rsp = client.toBlocking().exchange(HttpRequest.GET("/bookRestful"), String)

        then:
        noExceptionThrown()
        rsp.status() == HttpStatus.OK

        and: 'the book persisted in BootStrap is retrieved'
        List<Map<String, Object>> json = new JsonSlurper().parseText(rsp.body())
        json[0]['id'] != null
        json[0]['title'] == 'Example Book'
        json[0]['lastUpdated'] != null
        json[0]['dateCreated'] != null

        cleanup:
        client.close()
    }
}
