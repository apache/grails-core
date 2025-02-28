package functionaltests

import grails.testing.mixin.integration.Integration
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import spock.lang.Specification

@Integration(applicationClass = Application)
class AtResourceSpec extends Specification {

    def "A domain class annotated with @Resources exposes an endpoint"() {
        given:
        String baseUrl = "http://localhost:$serverPort"
        HttpClient client  = HttpClient.create(new URL(baseUrl))

        when:
        HttpResponse<String> rsp = client.toBlocking().exchange(HttpRequest.GET("/stuffs"), String)

        then:
        noExceptionThrown()
        rsp.status() == HttpStatus.OK

        cleanup:
        client.close()
    }


}
