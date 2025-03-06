package functional.tests

class Circular {

    String name
    MyEnum myEnum

    static belongsTo = [parent: Circular]

    static hasMany = [circulars: Circular]

    static constraints = {
        parent(nullable: true)
    }
}
