package grails.test.app

import java.time.OffsetDateTime

class UnsupportedType {

    OffsetDateTime customType

    static constraints = {
    }

    static graphql = true
}
