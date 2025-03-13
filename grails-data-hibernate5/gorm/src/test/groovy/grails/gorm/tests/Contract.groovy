package grails.gorm.tests

import grails.gorm.annotation.Entity

/**
 * Created by graemerocher on 21/10/16.
 */
@Entity
class Contract {
    BigDecimal salary
    static belongsTo = [player:Player]
}
