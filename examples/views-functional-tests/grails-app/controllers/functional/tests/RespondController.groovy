package functional.tests

import grails.artefact.Controller
import grails.artefact.controller.RestResponder

class RespondController implements Controller, RestResponder {

    def index() {
        respond(DomainObject.EXAMPLE_MAP())
    }
}

