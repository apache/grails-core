package functional.tests

import grails.rest.RestfulController

class ProductController extends RestfulController<Product> {
    static responseFormats = ['json']

    ProductController() {
        super(Product)
    }

    /**
     * Lists all resources up to the given maximum
     *
     * @param max The maximum
     * @return A list of resources
     */
    @Override
    def index(Integer max) {
        params.max = Math.min(max ?: 10, 100)
        return [
                productList : listAllResources(params),
                productCount: countResources(),
                max         : params.max,
                offset      : params.int("offset") ?: 0,
                sort        : params.sort,
                order       : params.order
        ]
    }
}
