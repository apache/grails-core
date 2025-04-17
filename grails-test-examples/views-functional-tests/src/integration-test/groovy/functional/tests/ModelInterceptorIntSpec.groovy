package functional.tests

import grails.testing.mixin.integration.Integration
import grails.testing.spock.RunOnce
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Unroll

@Integration(applicationClass = Application)
class ModelInterceptorIntSpec extends HttpClientSpec {

    @RunOnce
    @BeforeEach
    void init() {
        super.init()
    }

    @Autowired
    ModelInterceptor modelInterceptor

    @Unroll
    void "interceptor should get model from #controller"() {
        given:
        def request = HttpRequest.GET("/$controller")
                .contentType(MediaType.APPLICATION_JSON)
        HttpResponse response = client.toBlocking().exchange(request, List<Map>)

        expect:
        response.status == HttpStatus.OK
        modelInterceptor.latestModel != null

        where:
        controller << ["modelAndView",
                       "respond",
                       "return"]
    }
}
