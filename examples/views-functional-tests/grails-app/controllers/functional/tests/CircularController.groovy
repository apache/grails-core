package functional.tests


import grails.rest.*
import grails.converters.*

class CircularController extends RestfulController {

    static responseFormats = ['json']

    CircularController() {
        super(Circular)
    }

    def circular() {
        super.show()
    }
}
