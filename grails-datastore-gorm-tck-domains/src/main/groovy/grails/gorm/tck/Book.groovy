package grails.gorm.tck

import grails.persistence.Entity

@Entity
class Book implements Serializable {
    Long id
    Long version
    String author
    String title
    Boolean published = false

    static mapping = {
        published index:true
        title index:true
        author index:true
    }
}
