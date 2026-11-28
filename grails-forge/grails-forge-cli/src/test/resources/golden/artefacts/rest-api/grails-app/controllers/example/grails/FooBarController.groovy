package example.grails

import grails.rest.RestfulController

class FooBarController extends RestfulController<FooBar> {

    static responseFormats = ['json']

    FooBarController() {
        super(FooBar)
    }
}
