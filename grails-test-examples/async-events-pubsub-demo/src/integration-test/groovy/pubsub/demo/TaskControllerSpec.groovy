package pubsub.demo

import grails.testing.mixin.integration.Integration
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import spock.lang.AutoCleanup
import spock.lang.PendingFeature
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 30/05/2017.
 */
@Integration
class TaskControllerSpec extends Specification {

    @Shared
    @AutoCleanup
    HttpClient client

    void setup() {
        client = HttpClient.create("http://localhost:$serverPort".toURL())
    }

    @PendingFeature(reason = '''
        For some reason the response body is blank with bootTestRun.
        However, when starting the application with bootRun,
        the response body is as expected.
    ''')
    void 'test async error handling'() {

        when: 'we invoke an endpoint that throws an exception'
            def request = HttpRequest.GET('/task/error')
            client.toBlocking().exchange(request, Argument.of(String), Argument.of(String))

        then: 'the response is as expected'
            def e = thrown(HttpClientResponseException)
            e.response.status == HttpStatus.INTERNAL_SERVER_ERROR
            e.response.body() == 'error occurred'
    }
}
