package grails.gorm.tests.belongsto

import grails.gorm.annotation.Entity

/**
 * Created by graemerocher on 22/08/2017.
 */
@Entity
class Face {
    Nose nose
    static constraints = {
    }
}