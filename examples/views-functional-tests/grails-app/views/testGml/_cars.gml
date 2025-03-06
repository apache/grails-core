model {
    Iterable<Map> cars
}
xmlDeclaration()
cars {
    cars.each {
        car(make: it.make, model: it.model)
    }
}