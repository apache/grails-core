package org.apache.grails.data.testing.tck.domains

import grails.gorm.annotation.Entity

@Entity
class Owner_Default_Uni_P {

    String name
    static hasMany = [children: ChildPersister]
}
