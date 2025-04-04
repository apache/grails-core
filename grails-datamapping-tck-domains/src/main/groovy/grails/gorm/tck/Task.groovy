package grails.gorm.tck

import grails.persistence.Entity

@Entity
class Task implements Serializable {
    Long id
    Long version
    Set tasks
    Task task
    String name

    static mapping = {
        name index:true
    }

    static hasMany = [tasks:Task]
}
