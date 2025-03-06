package functional.tests

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import grails.testing.spock.RunOnce
import grails.web.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import org.junit.jupiter.api.BeforeEach

@Integration(applicationClass = Application)
@Rollback
class CircularSpec extends HttpClientSpec {

    @RunOnce
    @BeforeEach
    void init() {
        super.init()
    }

    void "test deep rendering of circular domain relationships"() {
        when:"A GET is issued"
        HttpRequest request = HttpRequest.GET("/circular/show/1")
        HttpResponse<Map> resp = client.toBlocking().exchange(request, Map)
        def json = resp.body()

        then:"The REST resource is retrieved and the correct JSON is returned"
        resp.status == HttpStatus.OK
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
        json.id == 1
        json.name == "topLevel"
        json.myEnum == "BAR"
        json.circulars.size() == 2
        json.circulars.find { it.id == 3 }.parent.id == 1
        json.circulars.find { it.id == 2 }.parent.id == 1
    }

    void "test nested template rendering of circular domain relationships"() {
        when:"A GET is issued"
        HttpRequest request = HttpRequest.GET("/circular/circular/1")
        HttpResponse<Map> resp = client.toBlocking().exchange(request, Map)
        def json = resp.body()

        then:"The REST resource is retrieved and the correct JSON is returned"
        resp.status == HttpStatus.OK
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
        json.name == "topLevel"
        json.children.size() == 2
        json.children.find { it.name == "topLevel-3" }
        json.children.find { it.name == "topLevel-2" }
    }
}
