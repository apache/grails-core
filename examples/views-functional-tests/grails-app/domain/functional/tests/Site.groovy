package functional.tests

class Site {
    String name
    static belongsTo = [customer: Customer]
}
