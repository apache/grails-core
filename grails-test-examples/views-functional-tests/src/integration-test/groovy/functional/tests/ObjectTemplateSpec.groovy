package functional.tests

import grails.testing.mixin.integration.Integration
import grails.testing.spock.RunOnce
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import org.junit.jupiter.api.BeforeEach

@Integration
class ObjectTemplateSpec extends HttpClientSpec {

    @RunOnce
    @BeforeEach
    void init() {
        super.init()
    }

    void "Test that if there is a global /object/_object template it is rendered if no template found"() {
        when:"A POST is issued"
        HttpRequest request = HttpRequest.GET('/place/test')
        HttpResponse<String> rsp = client.toBlocking().exchange(request, String)

        then:"The correct response is returned"
        rsp.status() == HttpStatus.OK
        rsp.body() == '{"location":"UK","name":"London"}'
    }
}
