package functional.tests

class Bus extends Vehicle {
    String route

    static mapping = {
        discriminator column: 'vehicleClass'
    }

}
