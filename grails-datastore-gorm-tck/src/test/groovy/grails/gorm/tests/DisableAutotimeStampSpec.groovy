package grails.gorm.tests

import grails.persistence.Entity

/**
 * @author Graeme Rocher
 */
class DisableAutotimeStampSpec extends GormDatastoreSpec{


    void "Test that when auto timestamping is disabled the dateCreated and lastUpdated properties are not set"() {
        when:"An entity is persisted"
            def r = new Record(name: "Test")
            r.save(flush:true)
            session.clear()
            r = Record.get(r.id)

        then:"There are errors and dateCreated / lastUpdated were not set"
            r.lastUpdated == null
            r.dateCreated == null

        when:"The entity is saved successfully and updated"
            def d = new Date().parse('yyyy/MM/dd', '1973/07/21')
            r.lastUpdated = d
            r.dateCreated = d
            r.save(flush: true)
            session.clear()
            r = Record.get(r.id)

        then:"lastUpdated is not changed"
            r != null
            r.lastUpdated == d
            r.dateCreated == d
    }
    @Override
    List getDomainClasses() {
        [Record]
    }
}

@Entity
class Record {
    Long id
    String name
    Date dateCreated
    Date lastUpdated

    static constraints = {
        dateCreated nullable:true
        lastUpdated nullable:true
    }
    static mapping = {
        autoTimestamp false
    }
}
