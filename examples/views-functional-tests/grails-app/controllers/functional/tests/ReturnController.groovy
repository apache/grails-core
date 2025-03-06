package functional.tests

import grails.artefact.Controller
import grails.artefact.controller.RestResponder

class ReturnController implements Controller, RestResponder {

    def index() {
        return DomainObject.EXAMPLE_MAP()
    }
}

