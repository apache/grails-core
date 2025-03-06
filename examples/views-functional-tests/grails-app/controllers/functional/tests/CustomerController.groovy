package functional.tests

import grails.gorm.transactions.ReadOnly

@ReadOnly
class CustomerController {

    def index() {
        respond(customer: Customer.list(fetch: [sites: 'join']).get(0))
    }
}
