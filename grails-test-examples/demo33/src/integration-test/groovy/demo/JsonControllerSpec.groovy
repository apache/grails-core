package demo

import grails.testing.mixin.integration.Integration
import spock.lang.Specification

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Integration
class JsonControllerSpec extends Specification {

    void "test a json view is rendered"() {
        given:
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$serverPort/json/index"))
            .timeout(Duration.ofMinutes(2))
            .header("Accept", "application/json")
            .GET()
            .build()

        when:
        def response = HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString())

        then:
        response.body() == '{"foo":"bar"}'
    }

    void "test a html view is rendered"() {
        when:
        def response = new URL("http://localhost:$serverPort/json/index").getText()

        then:
        response.contains('<html><head><title></title></head><body>Testing</body></html>')
    }
}
