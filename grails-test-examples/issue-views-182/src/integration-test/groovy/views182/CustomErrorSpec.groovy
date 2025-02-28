package views182

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import grails.testing.spock.OnceBefore
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException

@Integration
@Rollback
class CustomErrorSpec extends HttpClientCommonSpec {

    @OnceBefore
    void init() {
        this.baseUrl = "http://localhost:$serverPort"
        this.client = HttpClient.create(new URL(baseUrl))
    }

    void 'it is possible to use gson views for handling exception errors'() {
        when: 'executing get to custom error'
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET("/customError"), Argument.of(String), Argument.of(String))

        then:
        HttpClientResponseException e = thrown()
        e.response.status == HttpStatus.INTERNAL_SERVER_ERROR
        e.response.body() == '{"message":"My custom exception handler","error":500}'
    }
}
