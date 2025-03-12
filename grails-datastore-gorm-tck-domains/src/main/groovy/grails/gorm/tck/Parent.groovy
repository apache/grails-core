package grails.gorm.tck

import grails.persistence.Entity

@Entity
class Parent implements Serializable {
    private static final long serialVersionUID = 1
    Long id
    String name
    Set<Child> children = []
    static hasMany = [children: Child]
}