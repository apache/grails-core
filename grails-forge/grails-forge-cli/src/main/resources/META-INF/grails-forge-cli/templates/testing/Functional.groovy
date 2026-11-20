@artifact.package@
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

import grails.testing.mixin.integration.Integration
import spock.lang.Specification

@Integration
class @artifact.name@Spec extends Specification {

    void "test the home page"() {
        when: "the home page is requested"
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:${serverPort}/")).build()
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())

        then: "the response is correct"
        response.statusCode() == 200
    }
}
