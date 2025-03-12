package grails.gorm.tck

import grails.persistence.Entity

@Entity
class Child implements Serializable {
    private static final long serialVersionUID = 1
    Long id
    Long version
    String name
}
