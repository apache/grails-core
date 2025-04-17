package functional.tests.plugin

import grails.compiler.GrailsCompileStatic
import grails.rest.RestfulController

@GrailsCompileStatic
class AuthorController extends RestfulController<Author> {

    static responseFormats = ['json']

    AuthorController() {
        super(Author)
    }

}
