package grails.plugin.formfields.mock

import grails.persistence.Entity

@Entity
class User {

    String email
    String password
    String confirmPassword

    static constraints = {
        email blank: false, email: true
        password blank: false
        confirmPassword bindable: true, validator: { value, self ->
            value == self.password
        }
    }

    static transients = ['confirmPassword']

}
