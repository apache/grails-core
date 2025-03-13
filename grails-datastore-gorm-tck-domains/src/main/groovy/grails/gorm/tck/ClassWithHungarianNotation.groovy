package grails.gorm.tck

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