package grails.gorm.tck

import grails.persistence.Entity

@Entity
class CommonTypes implements Serializable {
    Long id
    Long version
    Long l
    Byte b
    Short s
    Boolean bool
    Integer i
    URL url
    Date date
    Calendar c
    BigDecimal bd
    BigInteger bi
    Double d
    Float f
    TimeZone tz
    Locale loc
    Currency cur
    byte[] ba
}
