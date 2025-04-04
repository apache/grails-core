package grails.gorm.tests

import grails.gorm.DetachedCriteria
import grails.gorm.tck.Person

/**
 * Tests for using subqueries in criteria and where method calls
 *
 */
class SubquerySpec extends GormDatastoreSpec {

    def "Test subquery with projection and criteria with closure"() {
        given:"A bunch of people"
            createPeople()

        when:"We query for people above a certain age average"
            def results = Person.withCriteria {
                gt "age",  {
                    projections {
                        avg "age"
                    }
                }

                order "firstName"
            }

        then:"the correct results are returned"
            results.size() == 4
            results[0].firstName == "Barney"
            results[1].firstName == "Fred"
            results[2].firstName == "Homer"
            results[3].firstName == "Marge"
    }

    def "Test subquery with projection and criteria"() {
        given:"A bunch of people"
            createPeople()

        when:"We query for people above a certain age average"
            def results = Person.withCriteria {
                gt "age", new DetachedCriteria(Person).build {
                    projections {
                        avg "age"
                    }
                }

                order "firstName"
            }

        then:"the correct results are returned"
            results.size() == 4
            results[0].firstName == "Barney"
            results[1].firstName == "Fred"
            results[2].firstName == "Homer"
            results[3].firstName == "Marge"
    }

    def "Test subquery that returned multiple results and criteria"() {
        given:"A bunch of people"
            createPeople()

        when:"We query for people above a certain age average"
            def results = Person.withCriteria {
                gtAll "age", new DetachedCriteria(Person).build {
                    projections {
                        property "age"
                    }
                    between 'age', 5, 39
                }

                order "firstName"
            }

        then:"the correct results are returned"
            results.size() == 3
            results[0].firstName == "Fred"
            results[1].firstName == "Homer"
            results[2].firstName == "Marge"
    }

    def "Test subquery that returned multiple results and criteria using a closure" () {
        given:"A bunch of people"
            createPeople()

        when:"We query for people above a certain age average"
            def results = Person.withCriteria {
                gtAll "age", {
                    projections {
                        property "age"
                    }
                    between 'age', 5, 39
                }

                order "firstName"
            }

        then:"the correct results are returned"
            results.size() == 3
            results[0].firstName == "Fred"
            results[1].firstName == "Homer"
            results[2].firstName == "Marge"
    }

    protected def createPeople() {
        new Person(firstName: "Homer", lastName: "Simpson", age:45).save()
        new Person(firstName: "Marge", lastName: "Simpson", age:40).save()
        new Person(firstName: "Bart", lastName: "Simpson", age:9).save()
        new Person(firstName: "Lisa", lastName: "Simpson", age:7).save()
        new Person(firstName: "Barney", lastName: "Rubble", age:35).save()
        new Person(firstName: "Fred", lastName: "Flinstone", age:41).save()
    }
}
