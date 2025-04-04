package org.grails.datastore.gorm

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

class NotNullQuerySpec extends GormDatastoreSpec {

    void "Test query of null value with dynamic finder"() {
        given:
            new NullMe(name:"Bob", job:"Builder").save()
            new NullMe(name:"Fred").save()

        when:
            def results = NullMe.findAllByJobIsNull()

        then:
            results.size() == 1
            results[0].name == "Fred"

        when:
            results = NullMe.findAllByJobIsNotNull()

        then:
            results.size() == 1
            results[0].name == "Bob"
    }

    void "Test query of null value with criteria query"() {
        given:
            new NullMe(name:"Bob", job:"Builder").save()
            new NullMe(name:"Fred").save()

        when:
            def results = NullMe.withCriteria { isNull "job" }

        then:
            results.size() == 1
            results[0].name == "Fred"

        when:
            results = NullMe.withCriteria { isNotNull "job" }

        then:
            results.size() == 1
            results[0].name == "Bob"
    }

    void "Test query of null value with dynamic finder on association"() {
        given:
            new NullMe(name:"Bob", other: new NullOther(name: 'stuff').save()).save()
            new NullMe(name:"Fred").save()

        when:
            def results = NullMe.findAllByOtherIsNull()

        then:
            results.size() == 1
            results[0].name == "Fred"

        when:
            results = NullMe.findAllByOtherIsNotNull()

        then:
            results.size() == 1
            results[0].name == "Bob"
    }

    void "Test query of null value with criteria query on association"() {
        given:
            new NullMe(name:"Bob", other: new NullOther(name: 'stuff').save()).save()
            new NullMe(name:"Fred").save()

        when:
            def results = NullMe.withCriteria { isNull "other" }

        then:
            results.size() == 1
            results[0].name == "Fred"

        when:
            results = NullMe.withCriteria { isNotNull "other" }

        then:
            results.size() == 1
            results[0].name == "Bob"
     }

    @Override
    List getDomainClasses() {
        [NullMe, NullOther]
    }
}

@Entity
class NullMe {
    Long id
    String name
    String job
    NullOther other

    static constraints = {
        job nullable:true
        other nullbale:true
    }

    static mapping = {
        job index:true
    }
}

@Entity
class NullOther {
    Long id
    String name

    static constraints = {
        job nullable:true
    }

    static mapping = {
        job index:true
    }
}


