package functional.tests

class Product {

    String name
    String description
    BigDecimal price

    static constraints = {
        name blank: false
        description blank: true, nullable: true
        price nullable: false
    }
}
