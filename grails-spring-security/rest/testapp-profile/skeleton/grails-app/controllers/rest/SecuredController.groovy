package rest

import grails.plugin.springsecurity.annotation.Secured

class SecuredController {

    def springSecurityService

    @Secured(['ROLE_USER'])
    def index() {
        render springSecurityService.principal.username
    }

    @Secured(['ROLE_SUPER_ADMIN'])
    def superAdmin() {
        render springSecurityService.principal.username
    }
}
