package functional.tests

import grails.rest.RestfulController

/**
 * Created by graemerocher on 02/01/2017.
 */
class ProductController extends RestfulController<Product> {

    static responseFormats = ['json']

    ProductController() {
        super(Product)
    }
}
