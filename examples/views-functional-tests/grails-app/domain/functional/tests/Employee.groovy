package functional.tests

class Employee {
    String name
    static belongsTo = Project
    static hasMany = [projects: Project]
}
