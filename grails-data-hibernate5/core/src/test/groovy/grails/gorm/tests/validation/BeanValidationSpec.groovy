package grails.gorm.tests.validation

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.validator.constraints.NotBlank
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import jakarta.validation.constraints.Digits

/**
 * Created by graemerocher on 07/04/2017.
 */
class BeanValidationSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore hibernateDatastore = new HibernateDatastore(Bean)

    @Rollback
    void "test bean validation API validate on save"() {
        given:"A an invalid instance"
        Bean bean = new Bean(name:"", price:600.12034)
        when:"the bean is saved"
        bean.save()

        then:"the errors are correct"
        bean.hasErrors()
        bean.errors.allErrors.size() == 2
        bean.errors.hasFieldErrors("price")
        bean.errors.hasFieldErrors("name")
    }
}

@Entity
class Bean {
    @NotBlank
    String name
    @Digits(integer = 6, fraction = 2)
    Double price
}
