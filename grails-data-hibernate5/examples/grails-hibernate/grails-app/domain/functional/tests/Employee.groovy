package functional.tests

class Employee extends Person {

    static belongsTo = [
            business: Business
    ]

}