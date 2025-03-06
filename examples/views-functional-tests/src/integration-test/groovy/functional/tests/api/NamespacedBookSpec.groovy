package functional.tests.api

import com.fasterxml.jackson.databind.ObjectMapper
import functional.tests.Application
import functional.tests.HttpClientSpec
import grails.testing.mixin.integration.Integration
import grails.testing.spock.RunOnce
import grails.web.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import org.junit.jupiter.api.BeforeEach
import spock.lang.Issue
import spock.lang.Shared

@Integration(applicationClass = Application)
class NamespacedBookSpec extends HttpClientSpec {

    @Shared
    ObjectMapper objectMapper

    def setup() {
        objectMapper = new ObjectMapper()
    }

    @RunOnce
    @BeforeEach
    void init() {
        super.init()
    }

    void 'test view rendering with a namespace'() {
        when: 'A request is sent to a controller with a namespace'
        HttpRequest request = HttpRequest.GET('/api/book')
        HttpResponse<Map> rsp = client.toBlocking().exchange(request, Map)

        then: 'The rsponse is correct'
            rsp.status() == HttpStatus.OK
            rsp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
            rsp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
            rsp.body().api == 'version 1.0 (Namespaced)'
            rsp.body().title == 'API - The Shining'
    }

    void 'test nested template rendering with a namespace'() {
        when: 'A request is sent to a controller with a namespace'
        HttpRequest request = HttpRequest.GET('/api/book/nested')
        HttpResponse<Map> rsp = client.toBlocking().exchange(request, Map)

        then: 'The rsponse contains the child template'
        rsp.status() == HttpStatus.OK
        rsp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        rsp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
        rsp.body().foo == 'bar'
    }

    void 'test the correct content type is chosen (json)'() {
        when: 'A request is sent to a controller with a namespace'
        HttpRequest request = HttpRequest.GET('/api/book')
        HttpResponse<Map> rsp = client.toBlocking().exchange(request, Map)

        then: 'The response contains the child template'
        rsp.status() == HttpStatus.OK
        rsp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        rsp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
        !rsp.body()['_links']
        rsp.body().api == 'version 1.0 (Namespaced)'
        rsp.body().title == 'API - The Shining'
    }

    void 'test the correct content type is chosen (hal)'() {
        when: 'A request is sent to a controller with a namespace'
        HttpRequest request = HttpRequest.GET('/api/book').accept(MediaType.APPLICATION_HAL_JSON_TYPE)
        HttpResponse<String> rsp = client.toBlocking().exchange(request, String)
        Map body = objectMapper.readValue(rsp.body(), Map)

        then: 'The response contains the child template'
        rsp.status() == HttpStatus.OK
        rsp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        rsp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/hal+json;charset=UTF-8'
        body['_links']
        body.api == 'version 1.0 (Namespaced HAL)'
        body.title == 'API - The Shining'
    }

    void 'test render(view: "..", model: ..) in controllers with namespaces works'() {
        when: 'A request is sent to a controller with a namespace'
        HttpRequest request = HttpRequest.GET('/api/book/testRender')
        HttpResponse<Map> rsp = client.toBlocking().exchange(request, Map)

        then: 'The rsponse is correct'
        rsp.status() == HttpStatus.OK
        rsp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        rsp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
        rsp.body().api == 'version 1.0 (Namespaced)'
        rsp.body().title == 'API - The Shining'
    }

    void 'test rspond(foo, view: ..) in controllers with namespaces works'() {
        when: 'A request is sent to a controller with a namespace'
        HttpRequest request = HttpRequest.GET('/api/book/testRespond')
        HttpResponse<Map> rsp = client.toBlocking().exchange(request, Map)

        then: 'The response is correct'
        rsp.status() == HttpStatus.OK
        rsp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        rsp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
        rsp.body().api == 'version 1.0 (Namespaced)'
        rsp.body().title == 'API - The Shining'
    }

    void 'test respond(foo, view: ..) in controllers with namespaces works, view outside of namespace'() {
        when: 'A request is sent to a controller with a namespace'
        HttpRequest request = HttpRequest.GET('/api/book/testRespondOutsideNamespace')
        HttpResponse<Map> rsp = client.toBlocking().exchange(request, Map)

        then: 'The response is correct'
        rsp.status() == HttpStatus.OK
        rsp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        rsp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
        rsp.body().api == 'version 1.0 (Non-Namespaced)'
        rsp.body().title == 'API - The Shining'
    }

    @Issue('https://github.com/grails/grails-views/issues/186')
    void 'test view rendering with a namespace from a map'() {
        when: 'A request is sent to a controller with a namespace'
        HttpRequest request = HttpRequest.GET('/api/book/message')
        HttpResponse<Map> rsp = client.toBlocking().exchange(request, Map)

        then: 'The response is correct'
        rsp.status() == HttpStatus.OK
        rsp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        rsp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
        rsp.body().message == 'Controller says Hello API'
    }
}
