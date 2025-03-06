package functional.tests

import com.fasterxml.jackson.databind.ObjectMapper
import grails.testing.mixin.integration.Integration
import grails.testing.spock.RunOnce
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import org.junit.jupiter.api.BeforeEach
import spock.lang.Shared

@Integration(applicationClass = Application)
class TestGsonControllerSpec extends HttpClientSpec {

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

    void 'Test that responding with a map is possible'() {
        when: 'When JSON is requested'
        HttpRequest request = HttpRequest.GET('/testGson/testRespondWithMap')
        HttpResponse<String> resp = client.toBlocking().exchange(request, String)

        then: 'The JSON view is rendered'
        resp.body() == '{"message":"two"}'
    }

    void 'Test that responding with a map is possible with object template'() {
        when: 'When JSON is requested'
        HttpRequest request = HttpRequest.GET('/testGson/testRespondWithMapObjectTemplate.json')
        HttpResponse<String> resp = client.toBlocking().exchange(request, String)

        then: 'The JSON view is rendered'
        resp.body() == '{"one":"two"}'

    }
    
    void 'Test that it is possible to use the template engine directly'() {
        when: 'When JSON is requested'
        HttpRequest request = HttpRequest.GET('/testGson/testTemplateEngine')
        HttpResponse<String> resp = client.toBlocking().exchange(request, String)

        then: 'The JSON view is rendered'
        objectMapper.readTree(resp.body()) == objectMapper.readTree('''
            {
                "title": "The Stand",
                "timeZone": "America/New_York",
                "vendor": "MyCompany"
            }
        ''')
    }

    void 'Test the respond method returns a GSON view for JSON request'() {
        when: 'When JSON is requested'
        HttpRequest request = HttpRequest.GET('/testGson/testRespond.json')
        HttpResponse<String> resp = client.toBlocking().exchange(request, String)

        then:'The JSON view is rendered'
        resp.body() == '{"test":{"name":"Bob"}}'

        when: 'When HTML is requested'
        request = HttpRequest.GET('/testGson/testRespond.html')
        resp = client.toBlocking().exchange(request, String)

        then:'The GSP is rendered'
        resp.body().contains('<h1>Test Bob HTML</h1>')
    }

    void 'Test the respond method returns a GSON named after the domain view for JSON request'() {
        when: 'When JSON is requested'
        HttpRequest request = HttpRequest.GET('/testGson/testRespondWithTemplateForDomain.json')
        HttpResponse<String> resp = client.toBlocking().exchange(request, String)

        then: 'The JSON view is rendered'
        objectMapper.readTree(resp.body()) == objectMapper.readTree('''
            {
                "test": {
                    "name": "Bob",
                    "age": 60
                }
            }
        ''')
    }

    void 'Test template rendering works'() {
        when: 'A view that renders templates is rendered'
        HttpRequest request = HttpRequest.GET('/testGson/testTemplate.json')
        HttpResponse<String> resp = client.toBlocking().exchange(request, String)

        then: 'The result is correct'
        objectMapper.readTree(resp.body()) == objectMapper.readTree('''
            {
                "test": {
                    "name": "Bob",
                    "child": {
                        "child": {
                            "name": "Joe",
                            "age": 10
                        }
                    },
                    "children": [
                        {
                            "child": {
                                "name": "Joe",
                                "age": 10
                            }
                        }
                    ]
                }
            }
        ''')
    }

    void 'Test views from plugins are rendered'() {
        when: 'A view that renders templates is rendered'
        HttpRequest request = HttpRequest.GET('/testGson/testGsonFromPlugin')
        HttpResponse<String> resp = client.toBlocking().exchange(request, String)

        then: 'The result is correct'
        resp.body() == '{"message":"Hello from Plugin"}'
    }

    void 'Test view that inherits from plugins are rendered'() {
        when:
        HttpRequest request = HttpRequest.GET('/testGson/testInheritsFromPlugin')
        HttpResponse<String> resp = client.toBlocking().exchange(request, String)

        then:
        objectMapper.readTree(resp.body()) == objectMapper.readTree('''
            {
                "message": "Hello from Plugin Template",
                "foo": "bar"
            }
        ''')
    }

    void 'Test augmenting model'() {
        when: 'When JSON is requested'
        HttpRequest request = HttpRequest.GET('/testGson/testAugmentModel.json')
        HttpResponse<String> resp = client.toBlocking().exchange(request, String)

        then: 'The JSON view is rendered'
        objectMapper.readTree(resp.body()) == objectMapper.readTree('''
            {
                "test": {
                    "name": "John",
                    "age": 20
                }
            }
        ''')

        when: 'When HTML is requested'
        request = HttpRequest.GET('/testGson/testAugmentModel.html')
        resp = client.toBlocking().exchange(request, String)

        then: 'The GSP is rendered'
        resp.body().contains("<h1>Test John (20) HTML</h1>")
    }
}
