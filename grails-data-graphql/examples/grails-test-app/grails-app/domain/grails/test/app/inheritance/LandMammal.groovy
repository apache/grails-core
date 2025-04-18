package grails.test.app.inheritance

abstract class LandMammal extends Mammal {

    int limbCount = 4
    int moveSpeed = 30

    static constraints = {
    }

}
