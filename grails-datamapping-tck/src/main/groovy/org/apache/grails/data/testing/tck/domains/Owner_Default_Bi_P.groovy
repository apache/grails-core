package org.apache.grails.data.testing.tck.domains

import grails.gorm.annotation.Entity

@Entity
class Owner_Default_Bi_P {

    String name
    Set<Child_BT_Default_P> children
    static hasMany = [children: Child_BT_Default_P]
}
