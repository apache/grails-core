package functional.tests

import grails.testing.mixin.integration.Integration
import grails.testing.spock.RunOnce
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import org.junit.jupiter.api.BeforeEach

@Integration(applicationClass = Application)
class ProjectSpec extends HttpClientSpec {

    @RunOnce
    @BeforeEach
    void init() {
        super.init()
    }

    void "Test that circular references are correctly rendered for many to many relationship"() {
        given:"A rest client"

        when:"A POST is issued"
        HttpRequest request = HttpRequest.GET('/project')
        HttpResponse<Map> rsp = client.toBlocking().exchange(request, Map)

        then:
        rsp.status() == HttpStatus.OK

        when:
        Map project = rsp.body()

        then:"The correct response is returned"
        project.id == 1
        project.name == "Grails Views"
        project.employees.find { it.id == 1 }.name == "James Kleeh"
        project.employees.find { it.id == 1 }.project == null
        project.employees.find { it.id == 2 }.name == "Iván López"
        project.employees.find { it.id == 2 }.project == null
    }
}
