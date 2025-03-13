package grails.gorm.tck

import grails.persistence.Entity

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