package functional.tests

import grails.artefact.Controller
import grails.artefact.controller.RestResponder
import org.springframework.web.servlet.ModelAndView

class ModelAndViewController implements Controller, RestResponder {

    def index() {
        new ModelAndView('/object/_object', [
                object: DomainObject.EXAMPLE_MAP()
        ])
    }
}

