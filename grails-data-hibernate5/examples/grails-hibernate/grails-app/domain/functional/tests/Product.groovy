package functional.tests

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.validation.constraints.Digits

/**
 * Created by graemerocher on 02/01/2017.
 */
@Entity
class Product {

    @Id
    @GeneratedValue
    Long myId
    String name

    @Digits(integer = 6, fraction = 2)
    String price

}
