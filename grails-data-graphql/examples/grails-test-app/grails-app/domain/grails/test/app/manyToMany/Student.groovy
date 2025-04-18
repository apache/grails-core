package grails.test.app.manyToMany

class Student {
    String name

    static hasMany = [classes: Classes]

    static graphql = true
}
