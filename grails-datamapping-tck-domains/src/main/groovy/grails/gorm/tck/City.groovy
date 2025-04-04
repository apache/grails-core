package grails.gorm.tck

import grails.persistence.Entity

@Entity
class City extends Location {
    BigDecimal latitude
    BigDecimal longitude
}
