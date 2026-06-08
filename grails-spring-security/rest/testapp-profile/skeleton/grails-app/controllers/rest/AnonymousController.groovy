package rest

import grails.plugin.springsecurity.annotation.Secured

@Secured(['permitAll'])
class AnonymousController {

    def index() {
        render "Hi"
    }
}
