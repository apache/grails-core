package grails.plugin.formfields.mock

import grails.persistence.Entity

@Entity
class Employee extends Person {

    int salary

    int getYearlySalary() {
        200 * salary
    }

    static transients = ['yearlySalary']
}
