package grails.gorm.tests

import grails.persistence.Entity

/**
 * @author sdelamo
 */
@Entity
class ClassWithHungarianNotation implements Serializable {
    Integer iSize

    static constraints = {
        iSize nullable:true
    }
}