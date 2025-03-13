package grails.gorm.tck

import grails.persistence.Entity

@Entity
class TestPlayer implements Serializable {
    Long id
    String name
    List<String> attributes

}