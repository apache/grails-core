package org.sitemesh.grails.plugins.sitemesh3

class DemoController {

    def index() { // Force layout
        render view:'index', layout: 'bootstrap5'
    }

    def renderText() {
        render text: '<p>Hello World</p>', contentType: 'text/html'
    }

    def chaining() {} // Multiple layouts
    def jsp() { render view:'hello' } // JSP page with layout
    def viewException() {} // Exception in a view

    def exception() { // Exception from a controller.
        throw new RuntimeException("Whoops, why would you ever want to see an exception??")
    }

    // Use Controller to handle 500 error.
    def error500() {
        def exception = request.exception?:request.getAttribute('jakarta.servlet.error.exception')
        Map model = [error:exception?.message]
        if (request.forwardURI?.endsWith('.json')) {
            params.format = 'json'
        }
        respond model, view:'/error'
    }
}