package grails.test.app

import org.codehaus.groovy.util.HashCodeHelper

class SimpleComposite implements Serializable {

    String title
    String description

    UUID someUUID

    static mapping = {
        id composite: ['title', 'description']
    }

    int hashCode() {
        int hashCode = HashCodeHelper.initHash()
        if (title) {
            hashCode = HashCodeHelper.updateHash(hashCode, title)
        }
        if (description) {
            hashCode = HashCodeHelper.updateHash(hashCode, description)
        }
        hashCode
    }

    @Override
    boolean equals(Object other) {
        if (other instanceof SimpleComposite) {
            other.title == title && other.description == description
        }
    }

    static graphql = true
}