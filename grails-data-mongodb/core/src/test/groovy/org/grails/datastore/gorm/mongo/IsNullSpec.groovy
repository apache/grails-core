package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

import spock.lang.Issue

class IsNullSpec extends GormDatastoreSpec {

    @Issue('GPMONGODB-164')
    void "Test isNull works in a criteria query"() {
        given:"Some test data"
            new Elephant(name: "Dumbo").save(validate:false)
            new Elephant(name: "Big Daddy", trunk:new Trunk(length: 10).save()).save(flush:true,validate:false)
            session.clear()

        when:"A entity is queried with isNull"
            def results = Elephant.withCriteria {
                isNull 'trunk'
            }

        then:"The correct results are returned"
            results.size() == 1
            results[0].name == "Dumbo"

        when:"A entity is queried with isNotNull"
            results = Elephant.withCriteria {
                isNotNull 'trunk'
            }

        then:"The correct results are returned"
            results.size() == 1
            results[0].name == "Big Daddy"
    }

    @Issue('GPMONGODB-164')
    void "Test isNull works in a dynamic finder"() {
        given:"Some test data"
        new Elephant(name: "Dumbo").save(validate:false)
        new Elephant(name: "Big Daddy", trunk:new Trunk(length: 10).save()).save(flush:true,validate:false)
        session.clear()

        when:"A entity is queried with isNull"
        def results = Elephant.findAllByTrunkIsNull()

        then:"The correct results are returned"
        results.size() == 1
        results[0].name == "Dumbo"

        when:"A entity is queried with isNotNull"
        results = Elephant.findAllByTrunkIsNotNull()

        then:"The correct results are returned"
        results.size() == 1
        results[0].name == "Big Daddy"
    }

    @Override
    List getDomainClasses() {
        [Elephant, Trunk]
    }
}

@Entity
class Elephant {
    Long id
    String name
    Trunk trunk
    static mapping = {
        trunk nullable:true
    }
}

@Entity
class Trunk {
    Long id
    int length
}
