package rest

import grails.converters.JSON
import grails.plugin.springsecurity.annotation.Secured
import grails.plugin.springsecurity.rest.token.AccessToken

class JwtController {

    def springSecurityService

    @Secured(['ROLE_USER'])
    def claims() {
        AccessToken accessToken = springSecurityService.authentication as AccessToken
        render accessToken.accessTokenJwt.JWTClaimsSet.claims as JSON
    }

}
