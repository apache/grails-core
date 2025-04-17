package functional.tests

import com.fasterxml.jackson.databind.ObjectMapper
import grails.testing.mixin.integration.Integration
import grails.testing.spock.RunOnce
import grails.web.http.HttpHeaders
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientResponseException
import org.junit.jupiter.api.BeforeEach
import spock.lang.Shared

@Integration(applicationClass = Application)
class BookSpec extends HttpClientSpec {

    @Shared
    ObjectMapper objectMapper

    def setupSpec() {
        objectMapper = new ObjectMapper()
    }

    @RunOnce
    @BeforeEach
    void init() {
        super.init()
    }

    void 'Test errors view rendering'() {
        when: 'A POST is issued with a missing title'
        HttpRequest request = HttpRequest.POST('/books', [title: ''])
        client.toBlocking().exchange(request, Argument.of(String), Argument.of(String))

        then: 'The proper error is returned'
        HttpClientResponseException e = thrown()
        e.response.status == HttpStatus.UNPROCESSABLE_ENTITY
        e.response.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        // This has changed somewhere along the way
        // e.response.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/vnd.error;charset=UTF-8'
        // to ->
        e.response.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
        objectMapper.readTree(e.response.body().toString()) == objectMapper.readTree('''
            {
                "errors": [
                    {
                        "object": "functional.tests.Book",
                        "field": "title",
                        "rejected-value": null,
                        "message": "Property [title] of class [class functional.tests.Book] cannot be null"
                    }
                ]
            }
        ''')
    }

    void 'Test REST view rendering'() {
        when: 'A GET is issued to get all books'
        HttpRequest request = HttpRequest.GET('/books')
        def resp = client.toBlocking().exchange(request, String)

        then: 'The response is correct'
        resp.status == HttpStatus.OK
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
        resp.body() == '[]'

        when: 'A POST is issued to create a new book'
        request = HttpRequest.POST('/books', new SaveBookVM(title: 'The Stand'))
        resp = client.toBlocking().exchange(request, Map)

        then: 'The REST resource is created and the correct JSON is returned'
        resp.status == HttpStatus.CREATED
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
        resp.body()
        resp.body().id == 1
        resp.body().timeZone == 'America/New_York'
        resp.body().title == 'The Stand'
        resp.body().vendor == 'MyCompany'

        when: 'A GET request is issued'
        request = HttpRequest.GET("/books/${resp.body().id}")
        resp = client.toBlocking().exchange(request, Map)

        then: 'The response is correct'
        resp.status == HttpStatus.OK
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
        resp.body()
        resp.body().id == 1
        resp.body().timeZone == 'America/New_York'
        resp.body().title == 'The Stand'
        resp.body().vendor == 'MyCompany'

        when: 'A PUT is issued'
        resp = client.toBlocking().exchange(HttpRequest.PUT("/books/${resp.body().id}", new SaveBookVM(title: 'The Changeling')), Map)

        then: 'The resource is updated'
        resp.status == HttpStatus.OK
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
        resp.body()
        resp.body().id == 1
        resp.body().timeZone == 'America/New_York'
        resp.body().title == 'The Changeling'
        resp.body().vendor == 'MyCompany'

        when: 'A GET is issued for all books'
        request = HttpRequest.GET('/books')
        resp = client.toBlocking().exchange(request, String)

        then: 'The response is correct'
        resp.status == HttpStatus.OK
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
        objectMapper.readTree(resp.body()) == objectMapper.readTree('''
            [
                {
                    "id": 1,
                    "title": "The Changeling",
                    "timeZone": "America/New_York",
                    "vendor": "MyCompany"
                }
            ]
        ''')

        when: 'A GET is issued for all books with excludes'
        request = HttpRequest.GET('/books/listExcludes?testParam=3')
        resp = client.toBlocking().exchange(request, String)

        then: 'Access to config and params works'
        resp.status == HttpStatus.OK
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
        objectMapper.readTree(resp.body()) == objectMapper.readTree('''
            [
                {
                    "id": 1,
                    "timeZone": "America/New_York",
                    "title": "The Changeling",
                    "vendor": "ConfigVendor",
                    "fromParams": 3
                }
            ]
        ''')

        when: 'A GET is issued for all books with excludes'
        request = HttpRequest.GET('/books/listExcludesRespond?testParam=4')
        resp = client.toBlocking().exchange(request, String)

        then: 'view rendering works with a map with respond'
        resp.status == HttpStatus.OK
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
        objectMapper.readTree(resp.body()) == objectMapper.readTree('''
            [
                {
                    "id": 1,
                    "timeZone": "America/New_York",
                    "vendor": "ConfigVendor",
                    "fromParams": 4
                }
            ]
        ''')

        when: 'A GET is issued for a specific book rendered by a template'
        request = HttpRequest.GET('/books/showWithParams/1?expand=foo')
        resp = client.toBlocking().exchange(request, Map)


        then: 'view rendering with template passes parameters'
        resp.status == HttpStatus.OK
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
        resp.body().paramsFromView == resp.body().book['paramsFromTemplate']

    }

    void 'View parameter passed to the render method can be used for non-standard view locations'() {
        when: 'A GET is issued to a request with a template at a non-standard location'
        HttpRequest request = HttpRequest.GET('/books/non-standard-template')
        HttpResponse<String> resp = client.toBlocking().exchange(request, String)

        then: 'The template was rendered successfully. The custom converter was also used'
        resp.status == HttpStatus.OK
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
        objectMapper.readTree(resp.body()) == objectMapper.readTree('''
            {
                "bookTitle": "template found",
                "custom": "Sally"
            }
        ''')
    }

    void 'Object type of list is used for model variable when rendering templates'() {
        when:
        HttpRequest request = HttpRequest.GET('/books/listCallsTmpl')
        HttpResponse<String> resp = client.toBlocking().exchange(request, String)

        then: 'The template was rendered successfully'
        resp.status == HttpStatus.OK
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
        resp.body() == '[{"title":"The Changeling"}]'
    }

    void 'Object type of list is used for model variable in addition to specified model when rendering templates'() {
        when:
        HttpRequest request = HttpRequest.GET("/books/listCallsTmplExtraData")
        HttpResponse<String> resp = client.toBlocking().exchange(request, String)

        then: 'The template was rendered successfully'
        resp.status == HttpStatus.OK
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
        objectMapper.readTree(resp.body()) == objectMapper.readTree('''
            [
                {
                    "title": "The Changeling",
                    "value": true
                }
            ]
        ''')
    }

    void 'Object type of list is used for model variable in addition to specified model and var when rendering templates'() {
        when:
        HttpRequest request = HttpRequest.GET('/books/listCallsTmplVar')
        HttpResponse<String> resp = client.toBlocking().exchange(request, String)

        then: 'The template was rendered successfully'
        resp.status == HttpStatus.OK
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
        objectMapper.readTree(resp.body()) == objectMapper.readTree('''
            [
                {
                    "title": "The Changeling",
                    "value": true
                }
            ]
        ''')
    }
}

class SaveBookVM {
    String title
}
