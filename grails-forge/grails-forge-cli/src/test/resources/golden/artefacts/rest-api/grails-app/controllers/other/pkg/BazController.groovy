package other.pkg

import grails.rest.RestfulController

class BazController extends RestfulController<Baz> {

    static responseFormats = ['json']

    BazController() {
        super(Baz)
    }
}
