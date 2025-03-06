package functional.tests

import grails.testing.mixin.integration.Integration
import grails.testing.spock.RunOnce
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import org.junit.jupiter.api.BeforeEach
import spock.lang.Issue

@Integration(applicationClass = Application)
class BulletinSpec extends HttpClientSpec {

    @RunOnce
    @BeforeEach
    void init() {
        super.init()
    }

    @Issue('https://github.com/grails/grails-views/issues/175')
    void 'test render collections with same objects'() {
        when: 'a GET is issued'
        HttpRequest request = HttpRequest.GET("/bulletin")
        HttpResponse<Map> resp = client.toBlocking().exchange(request, Map)
        Map json = resp.body()

        then: 'The REST resource is retrieved and the correct JSON is returned'
        resp.status == HttpStatus.OK
        json.content == 'Hi everyone!'

        and: 'the username is the same as the publicId'
        json.targetUsers.size() == 2
        json.targetUsers.every { it.username == it.publicId }
        json.contactUsers.size() == 3
        json.contactUsers.every { it.username == it.publicId }
    }
}
