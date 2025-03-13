package example

import grails.compiler.GrailsCompileStatic
import grails.persistence.Entity

@Entity
@GrailsCompileStatic
class Customer implements Serializable {

    String name

    @SuppressWarnings('unused')
    Customer() {
        // no-args constructor for proxying.
        // Usually added by ControllerDomainTransformer
        // from 'org.grails:grails-plugin-controllers'
    }

    Customer(Long id, String name) {
        this.id = id
        this.name = name
    }

    static mapping = {
        id generator: 'assigned'
    }
}
