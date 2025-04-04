package grails.gorm.tests

import grails.gorm.annotation.Entity

/**
 * Created by graemerocher on 27/06/16.
 */
class LastUpdateWithDynamicUpdateSpec extends GormDatastoreSpec {
    @Override
    List getDomainClasses() {
        [LastUpdateTestA, LastUpdateTestB, LastUpdateTestC]
    }

    void "lastUpdated should work for dynamic update and no versioning on TestA"() {
        given:
        def a = new LastUpdateTestA(name: 'David Estes')
        a.save(flush:true)
        a.refresh()
        def lastUpdated = a.lastUpdated
        sleep(5)
        when:
        a.name = "David R. Estes"
        a.save(flush:true)
        a.refresh()
        then:
        a.lastUpdated > lastUpdated
    }

    void "lastUpdated should work for dynamic update with version true TestB"() {
        given:
        def a = new LastUpdateTestB(name: 'David Estes')
        a.save(flush:true)
        a.refresh()
        def lastUpdated = a.lastUpdated
        sleep(5)
        when:
        a.name = "David R. Estes"
        a.save(flush:true)
        a.refresh()
        then:
        a.lastUpdated > lastUpdated
    }

    void "lastUpdated should work for dynamic update false and versioning on TestC"() {
        given:
        def a = new LastUpdateTestC(name: 'David Estes')
        a.save(flush:true)
        a.refresh()
        def lastUpdated = a.lastUpdated
        sleep(5)
        when:
        a.name = "David R. Estes"
        a.save(flush:true)
        a.refresh()
        then:
        a.lastUpdated > lastUpdated
    }


    void "autoTimestamp should work with updateAll for dynamic update false and versioning on TestC"() {
        given:
        def a = new LastUpdateTestC(name: 'David Estes')
        a.save(flush:true)
        a.refresh()
        def lastUpdated = a.lastUpdated
        sleep(5)
        when:
        LastUpdateTestC.where{
            eq 'id', a.id
        }.updateAll(name: 'David R. Estes')
        a.refresh()
        then:
        a.lastUpdated > lastUpdated
    }
}


@Entity
class LastUpdateTestA {

    String name
    Date dateCreated
    Date lastUpdated

    static mapping = {
        version false
        dynamicUpdate true
    }
}

@Entity
class LastUpdateTestB {
    String name
    Date dateCreated
    Date lastUpdated

    static mapping = {
        version true
        dynamicUpdate true
    }
}

@Entity
class LastUpdateTestC {
    String name
    Date dateCreated
    Date lastUpdated

    static mapping = {
        version true
        dynamicUpdate false
    }
    static constraints = {
    }
}
