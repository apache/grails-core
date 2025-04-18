package grails.test.app

import grails.test.app.pogo.Profile
import org.grails.gorm.graphql.entity.dsl.GraphQLMapping

class User {

    Integer addedNumbers //addedNumbers is calculated based on additional input properties

    User manager //self referencing toOne

    Profile profile //embedded pogo class
    Address address //embedded domain class

    static constraints = {
        manager nullable: true
    }

    static embedded = ['address', 'profile']

    static graphql = GraphQLMapping.build {
        add('firstNumber', Integer) {
            //don't include this property in the list of properties to return from operations
            output(false)
            nullable(false)
        }
        add('secondNumber', Integer) {
            //don't include this property in the list of properties to return from operations
            output(false)
            nullable(false)
        }
        //don't allow users to specify this property when creating or updating user instances
        property('addedNumbers', input: false)

    }
}
