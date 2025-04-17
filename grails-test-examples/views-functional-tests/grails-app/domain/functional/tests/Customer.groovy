package functional.tests

class Customer {
    String name
    static hasMany = [sites: Site]
}
