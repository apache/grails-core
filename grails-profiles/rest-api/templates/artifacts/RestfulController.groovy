@artifact.package@

import grails.rest.*
import grails.converters.*

class @artifact.name@Controller extends RestfulController<@artifact.name@> {
    static responseFormats = ['json', 'xml']
    @artifact.name@Controller() {
        super(@artifact.name@)
    }
}
