package example

import grails.testing.mixin.integration.Integration
import grails.testing.spock.OnceBefore
import spock.lang.Shared
import spock.lang.Specification
import io.micronaut.http.client.HttpClient

@Integration
class BookControllerSpec extends Specification {

    @Shared
    HttpClient client

    @OnceBefore
    void init() {
        String baseUrl = "http://localhost:$serverPort"
        this.client = HttpClient.create(baseUrl.toURL())
    }

    void 'test books can be fetched'() {
        expect:
        client.toBlocking().retrieve('/book/grails').contains('The definitive Guide to Grails 2')
        !client.toBlocking().retrieve('/book/grails').contains('Groovy in Action')

        client.toBlocking().retrieve('/book/groovy').contains('Groovy in Action')
        !client.toBlocking().retrieve('/book/groovy').contains('The definitive Guide to Grails 2')
    }
}