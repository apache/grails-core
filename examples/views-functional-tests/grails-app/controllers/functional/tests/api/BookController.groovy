package functional.tests.api

import functional.tests.Book


class BookController {

    static namespace = 'api'

    static responseFormats = ['json', 'hal']

    def index() {
        respond new Book(title: 'API - The Shining')
    }

    def nested() {
        respond new Book(title: 'API - The Shining')
    }

    def testRender() {
        render(view: "index", model: [book: new Book(title: 'API - The Shining')])
    }

    def testRespond() {
        respond(new Book(title: 'API - The Shining'), view: 'index')
    }

    def testRespondOutsideNamespace() {
        respond(new Book(title: 'API - The Shining'), view: 'indexOutsideNamespace')
    }

    def message() {
        [value: 'Hello API']
    }
}