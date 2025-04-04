package grails.gorm.tck

import grails.persistence.Entity

@Entity
class SimpleWidgetWithNonStandardId implements Serializable {
    Long myIdentifier
    Long version
    String name
    static mapping = {
        id name: 'myIdentifier'
    }
}