package functional.tests

import grails.testing.mixin.integration.Integration
import spock.lang.Specification

/**
 * Created by graemerocher on 19/05/16.
 */
@Integration(applicationClass = Application)
class GlobalTemplatesSpec extends Specification {

    void "Test errors view rendering"() {
        when:
        def url = new URL("http://localhost:$serverPort/place/show")
        def connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = 'GET'
        int responseCode = connection.responseCode
        def responseBody = connection.inputStream.text

        then:"The REST resource is created and the correct JSON is returned"
        responseCode == 200
        responseBody == '{"location":{"type":"Point","coordinates":[10.0,10.0]},"name":"London"}'
    }
}
