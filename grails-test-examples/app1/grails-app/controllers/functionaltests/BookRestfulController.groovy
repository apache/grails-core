package functionaltests

import grails.rest.RestfulController
import groovy.transform.CompileStatic

@CompileStatic
class BookRestfulController extends RestfulController<Book> {

    static responseFormats = ['json', 'xml']
    static namespace = 'v1'

    BookRestfulController() {
        super(Book)
    }
}