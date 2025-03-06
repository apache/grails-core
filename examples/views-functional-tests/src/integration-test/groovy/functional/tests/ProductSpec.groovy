package functional.tests

import com.fasterxml.jackson.databind.ObjectMapper
import grails.testing.mixin.integration.Integration
import grails.testing.spock.RunOnce
import grails.web.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import org.junit.jupiter.api.BeforeEach
import spock.lang.Shared

@Integration(applicationClass = Application)
class ProductSpec extends HttpClientSpec {

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

    void testEmptyProducts() {
        when:
        HttpRequest request = HttpRequest.GET('/products')
        HttpResponse<String> resp = client.toBlocking().exchange(request, String)
        Map body = objectMapper.readValue(resp.body(), Map)

        then:
        resp.status == HttpStatus.OK
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/hal+json;charset=UTF-8'

        and: 'The values returned are there'
        body.count == 0
        body.max == 10
        body.offset == 0
        body.sort == null
        body.order == null

        and: 'the hal _links attribute is present'
        body._links.size() == 1
        body._links.self.href.startsWith("${baseUrl}/product")

        and: 'there are no products yet'
        body._embedded.products.size() == 0
    }

    void testSingleProduct() {
        given:
        HttpRequest request = HttpRequest.POST('/products', [
                name: 'Product 1',
                description: 'product 1 description',
                price: 123.45
        ])

        when:
        HttpResponse<String> createResp = client.toBlocking().exchange(request, String)
        Map createBody = objectMapper.readValue(createResp.body(), Map)

        then:
        createResp.status == HttpStatus.CREATED

        when: 'We get the products'
        request = HttpRequest.GET('/products')
        HttpResponse<String> resp = client.toBlocking().exchange(request, String)
        Map body = objectMapper.readValue(resp.body(), Map)

        then:
        resp.status == HttpStatus.OK
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/hal+json;charset=UTF-8'

        and: 'The values returned are there'
        body.count == 1
        body.max == 10
        body.offset == 0
        body.sort == null
        body.order == null

        and: 'the hal _links attribute is present'
        body._links.size() == 1
        body._links.self.href.startsWith("${baseUrl}/product")

        and: 'the product is present'
        body._embedded.products.size() == 1
        body._embedded.products.first().name == 'Product 1'

        cleanup:
        resp = client.toBlocking().exchange(HttpRequest.DELETE("/products/${createBody.id}"))
        assert resp.status() == HttpStatus.OK
    }

    void 'test a page worth of products'() {
        given:
        def productsIds = []
        15.times { productNumber ->
            ProductVM product = new ProductVM(
                    name: "Product $productNumber",
                    description: "product ${productNumber} description",
                    price: productNumber + (productNumber / 100)
            )
            HttpResponse<String> createResp = client.toBlocking()
                    .exchange(HttpRequest.POST('/products', product), String)
            Map createBody = objectMapper.readValue(createResp.body(), Map)
            assert createResp.status == HttpStatus.CREATED
            productsIds << createBody.id
        }

        when: 'We get the products'
        HttpRequest request = HttpRequest.GET('/products')
        HttpResponse<String> resp = client.toBlocking().exchange(request, String)
        Map body = objectMapper.readValue(resp.body(), Map)

        then:
        resp.status == HttpStatus.OK
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/hal+json;charset=UTF-8'

        and: 'The values returned are there'
        body.count == 15
        body.max == 10
        body.offset == 0
        body.sort == null
        body.order == null

        and: 'the hal _links attribute is present'
        body._links.size() == 4
        body._links.self.href.startsWith("${baseUrl}/product")
        body._links.first.href.startsWith("${baseUrl}/product")
        body._links.next.href.startsWith("${baseUrl}/product")
        body._links.last.href.startsWith("${baseUrl}/product")

        and: 'the product is present'
        body._embedded.products.size() == 10

        cleanup:
        productsIds.each { id ->
            resp = client.toBlocking().exchange(HttpRequest.DELETE("/products/${id}"))
            assert resp.status() == HttpStatus.OK
        }
    }

    void 'test a middle page worth of products'() {
        given:
        def productsIds = []
        30.times { productNumber ->
            ProductVM product = new ProductVM(
                    name: "Product $productNumber",
                    description: "product ${productNumber} description",
                    price: productNumber + (productNumber / 100)
            )
            HttpResponse<String> createResp = client.toBlocking().exchange(HttpRequest.POST('/products', product), String)
            assert createResp.status == HttpStatus.CREATED
            Map createBody = objectMapper.readValue(createResp.body(), Map)
            productsIds << createBody.id
        }

        when: 'We get the products'
        HttpRequest request = HttpRequest.GET('/products?offset=10')
        HttpResponse<String> resp = client.toBlocking().exchange(request, String)
        Map body = objectMapper.readValue(resp.body(), Map)

        then:
        resp.status == HttpStatus.OK
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/hal+json;charset=UTF-8'

        and: 'The values returned are there'
        body.count == 30
        body.max == 10
        body.offset == 10
        body.sort == null
        body.order == null

        and: 'the hal _links attribute is present'
        body._links.size() == 5
        body._links.self.href.startsWith("${baseUrl}/product")
        body._links.first.href.startsWith("${baseUrl}/product")
        body._links.prev.href.startsWith("${baseUrl}/product")
        body._links.next.href.startsWith("${baseUrl}/product")
        body._links.last.href.startsWith("${baseUrl}/product")

        and: 'the product is present'
        body._embedded.products.size() == 10

        cleanup:
        productsIds.each { id ->
            resp = client.toBlocking().exchange(HttpRequest.DELETE("/products/${id}"))
            assert resp.status() == HttpStatus.OK
        }
    }
}

class ProductVM {
    String name
    String description
    BigDecimal price
}
