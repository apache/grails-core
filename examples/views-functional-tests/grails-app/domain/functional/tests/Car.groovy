package functional.tests

class Car extends Vehicle {
    String make
    String model
    Integer year

    static mapping = {
        discriminator column: 'vehicleClass'
        year column: '`year`'
    }
}
