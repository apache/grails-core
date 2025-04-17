package functional.tests

import grails.testing.spock.RunOnce
import io.micronaut.http.client.HttpClient
import org.junit.jupiter.api.BeforeEach
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class HttpClientSpec extends Specification {

    @Shared
    @AutoCleanup
    HttpClient client

    @Shared
    String baseUrl

    @RunOnce
    @BeforeEach
    void init() {
        this.baseUrl = "http://localhost:$serverPort"
        this.client = HttpClient.create(new URL(baseUrl))
    }
}
