package grails.gorm.tests

import spock.lang.Specification

/**
 * Tests for querying the size of collections etc.
 */
class SizeQuerySpec extends GormDatastoreSpec {

    void "Test sizeLe criterion"() {
        given: "A country with only 1 resident"
            Person p = new Person(firstName: "Fred", lastName: "Flinstone")
            Country c = new Country(name:"Dinoville")
                            .addToResidents(p)
                            .save(flush:true)

            new Country(name:"Springfield")
                            .addToResidents(firstName:"Homer", lastName:"Simpson")
                            .addToResidents(firstName:"Bart", lastName:"Simpson")
                            .addToResidents(firstName:"Marge", lastName:"Simpson")
                            .save(flush:true)

            new Country(name:"Miami")
                        .addToResidents(firstName:"Dexter", lastName:"Morgan")
                        .addToResidents(firstName:"Debra", lastName:"Morgan")
                        .save(flush:true)

            session.clear()

        when:"We query for countries with 1 resident"
            def results = Country.withCriteria {
                sizeLe "residents", 3
                order "name"
            }

        then:"We get the correct result back"
            results != null
            results.size() == 3
            results[0].name == 'Dinoville'
            results[1].name == 'Miami'
            results[2].name == 'Springfield'

        when:"We query for countries with 2 resident"
            results = Country.withCriteria {
                sizeLe "residents", 2
                order "name"
            }

        then:"We get the correct result back"
             results != null
             results.size() == 2
             results[0].name == 'Dinoville'
             results[1].name == 'Miami'

        when:"We query for countries with 2 residents"
            results = Country.withCriteria {
                sizeLe "residents", 1
            }

        then:"we get 1 result back"
            results.size() == 1
    }

    void "Test sizeLt criterion"() {
        given: "A country with only 1 resident"
            Person p = new Person(firstName: "Fred", lastName: "Flinstone")
            Country c = new Country(name:"Dinoville")
                            .addToResidents(p)
                            .save(flush:true)

            new Country(name:"Springfield")
                            .addToResidents(firstName:"Homer", lastName:"Simpson")
                            .addToResidents(firstName:"Bart", lastName:"Simpson")
                            .addToResidents(firstName:"Marge", lastName:"Simpson")
                            .save(flush:true)

            new Country(name:"Miami")
                        .addToResidents(firstName:"Dexter", lastName:"Morgan")
                        .addToResidents(firstName:"Debra", lastName:"Morgan")
                        .save(flush:true)

            session.clear()

        when:"We query for countries with 1 resident"
            def results = Country.withCriteria {
                sizeLt "residents", 3
                order "name"
            }

        then:"We get the correct result back"
            results != null
            results.size() == 2
            results[0].name == 'Dinoville'
            results[1].name == 'Miami'

        when:"We query for countries with 2 resident"
            results = Country.withCriteria {
                sizeLt "residents", 2
            }

        then:"We get the correct result back"
             results != null
             results.size() == 1
             results[0].name == 'Dinoville'

        when:"We query for countries with 2 residents"
            results = Country.withCriteria {
                sizeLt "residents", 1
            }

        then:"we get no results back"
            results.size() == 0
    }

    void "Test sizeGt criterion"() {
        given: "A country with only 1 resident"
            Person p = new Person(firstName: "Fred", lastName: "Flinstone")
            Country c = new Country(name:"Dinoville")
                            .addToResidents(p)
                            .save(flush:true)

            new Country(name:"Springfield")
                            .addToResidents(firstName:"Homer", lastName:"Simpson")
                            .addToResidents(firstName:"Bart", lastName:"Simpson")
                            .addToResidents(firstName:"Marge", lastName:"Simpson")
                            .save(flush:true)

            new Country(name:"Miami")
                        .addToResidents(firstName:"Dexter", lastName:"Morgan")
                        .addToResidents(firstName:"Debra", lastName:"Morgan")
                        .save(flush:true)

            session.clear()

        when:"We query for countries with 1 resident"
            def results = Country.withCriteria {
                sizeGt "residents", 1
                order "name"
            }

        then:"We get the correct result back"
            results != null
            results.size() == 2
            results[0].name == 'Miami'
            results[1].name == 'Springfield'

        when:"We query for countries with 2 resident"
            results = Country.withCriteria {
                sizeGt "residents", 2
            }

        then:"We get the correct result back"
             results != null
             results.size() == 1
             results[0].name == 'Springfield'

        when:"We query for countries with 2 residents"
            results = Country.withCriteria {
                sizeGt "residents", 5
            }

        then:"we get no results back"
            results.size() == 0
    }

    void "Test sizeGe criterion"() {
        given: "A country with only 1 resident"
            Person p = new Person(firstName: "Fred", lastName: "Flinstone")
            Country c = new Country(name:"Dinoville")
                            .addToResidents(p)
                            .save(flush:true)

            new Country(name:"Springfield")
                            .addToResidents(firstName:"Homer", lastName:"Simpson")
                            .addToResidents(firstName:"Bart", lastName:"Simpson")
                            .addToResidents(firstName:"Marge", lastName:"Simpson")
                            .save(flush:true)

            new Country(name:"Miami")
                        .addToResidents(firstName:"Dexter", lastName:"Morgan")
                        .addToResidents(firstName:"Debra", lastName:"Morgan")
                        .save(flush:true)

            session.clear()

        when:"We query for countries with 1 resident"
            def results = Country.withCriteria {
                sizeGe "residents", 1
                order "name"
            }

        then:"We get the correct result back"
            results != null
            results.size() == 3
            results[0].name == 'Dinoville'
            results[1].name == 'Miami'
            results[2].name == 'Springfield'

        when:"We query for countries with 2 resident"
            results = Country.withCriteria {
                sizeGe "residents", 2
                order "name"
            }

        then:"We get the correct result back"
             results != null
             results.size() == 2
             results[0].name == 'Miami'
             results[1].name == 'Springfield'

        when:"We query for countries with 2 residents"
            results = Country.withCriteria {
                sizeGe "residents", 5
            }

        then:"we get no results back"
            results.size() == 0
    }

    void "Test sizeEq criterion"() {
        given: "A country with only 1 resident"
            Person p = new Person(firstName: "Fred", lastName: "Flinstone")
            Country c = new Country(name:"Dinoville")
                            .addToResidents(p)
                            .save(flush:true)

            new Country(name:"Springfield")
                            .addToResidents(firstName:"Homer", lastName:"Simpson")
                            .addToResidents(firstName:"Bart", lastName:"Simpson")
                            .addToResidents(firstName:"Marge", lastName:"Simpson")
                            .save(flush:true)

            session.clear()

        when:"We query for countries with 1 resident"
            def results = Country.withCriteria {
                sizeEq "residents", 1
            }

        then:"We get the correct result back"
            results != null
            results.size() == 1
            results[0].name == 'Dinoville'

        when:"We query for countries with 3 resident"
            results = Country.withCriteria {
                sizeEq "residents", 3
            }

        then:"We get the correct result back"
             results != null
             results.size() == 1
             results[0].name == 'Springfield'

        when:"We query for countries with 2 residents"
            results = Country.withCriteria {
                sizeEq "residents", 2
            }

        then:"we get no results back"
            results.size() == 0
    }

    void "Test sizeNe criterion"() {
        given: "A country with only 1 resident"
            Person p = new Person(firstName: "Fred", lastName: "Flinstone")
            Country c = new Country(name:"Dinoville")
                            .addToResidents(p)
                            .save(flush:true)

            new Country(name:"Springfield")
                            .addToResidents(firstName:"Homer", lastName:"Simpson")
                            .addToResidents(firstName:"Bart", lastName:"Simpson")
                            .addToResidents(firstName:"Marge", lastName:"Simpson")
                            .save(flush:true)

            session.clear()

        when:"We query for countries that don't have 1 resident"
            def results = Country.withCriteria {
                sizeNe "residents", 1
            }

        then:"We get the correct result back"
            results != null
            results.size() == 1
            results[0].name == 'Springfield'

        when:"We query for countries who don't have 3 resident"
            results = Country.withCriteria {
                sizeNe "residents", 3
            }

        then:"We get the correct result back"
             results != null
             results.size() == 1
             results[0].name == 'Dinoville'

        when:"We query for countries with 2 residents"
            results = Country.withCriteria {
                and {
                    sizeNe "residents", 1
                    sizeNe "residents", 3
                }
            }

        then:"we get no results back"
            results.size() == 0
    }
}
