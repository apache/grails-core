// tag::wholeFile[]
package demo

import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
class Speaker {

    String firstName
    String lastName
    String name
    String email
    String bio

    static hasMany = [talks: Talk]

    static graphql = true // <1>

    static constraints = {
        email nullable: true, email: true
        bio nullable: true
    }

    static mapping = {
        bio type: 'text'
        name formula: 'concat(FIRST_NAME,\' \',LAST_NAME)'
        talks sort: 'id'
    }

}
// end::wholeFile[]