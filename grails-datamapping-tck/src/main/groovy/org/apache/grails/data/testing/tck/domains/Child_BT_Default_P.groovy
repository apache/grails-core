package org.apache.grails.data.testing.tck.domains

import grails.gorm.annotation.Entity

@Entity
class Child_BT_Default_P {
    String title
    static belongsTo = [owner: Owner_Default_Bi_P]
}
