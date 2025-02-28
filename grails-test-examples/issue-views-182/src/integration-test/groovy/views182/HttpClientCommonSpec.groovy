package views182

import grails.testing.spock.OnceBefore
import io.micronaut.http.client.HttpClient
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class HttpClientCommonSpec extends Specification {

    @Shared
    @AutoCleanup
    HttpClient client

    @Shared
    String baseUrl

    /**
     * Move this to the subclass as after Spock 2.0-M3-groovy-3.0, {code}OnceBefore{/code} does not
     * run when present in the super class.
     */
    /*@OnceBefore
    void init() {
        this.baseUrl = "http://localhost:$serverPort"
        this.client = HttpClient.create(new URL(baseUrl))
    }*/
}
