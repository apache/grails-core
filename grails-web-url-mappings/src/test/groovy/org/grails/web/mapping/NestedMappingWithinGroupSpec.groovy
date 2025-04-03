package org.grails.web.mapping

import grails.web.mapping.AbstractUrlMappingsSpec
import spock.lang.Issue

/**
 * Created by graemerocher on 25/10/16.
 */
class NestedMappingWithinGroupSpec extends AbstractUrlMappingsSpec {

    @Issue('https://github.com/apache/grails-core/issues/10246')
    void "test nested mapping within group"() {
        given:"A link generator with nested mappings withihn a group"
        def linkGenerator = getLinkGenerator {
            group("/api") {
                "/customer-stores"(resources: 'store') {
                    "/catalogs"(controller: 'catalog', action: 'index', method: 'GET')
                    "/catalogs/$catalogId/items"(controller: 'catalog', action: 'getItems', method: 'GET')
                }
            }
        }

        expect:"The generated links to be correct"
        linkGenerator.link(controller:"catalog", action:"index", method:"GET", params:[storeId:'1']) == 'http://localhost/api/customer-stores/1/catalogs'
        linkGenerator.link(controller:"catalog", action:"getItems", method:"GET", params:[storeId:'1', catalogId:'2']) == 'http://localhost/api/customer-stores/1/catalogs/2/items'
    }
}
