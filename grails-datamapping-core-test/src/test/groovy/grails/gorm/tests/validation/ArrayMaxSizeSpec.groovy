package grails.gorm.tests.validation

import grails.gorm.annotation.Entity
import grails.gorm.tests.GormDatastoreSpec
import org.grails.datastore.gorm.validation.constraints.registry.DefaultValidatorRegistry
import org.grails.datastore.mapping.model.MappingContext

/**
 * Created by graemerocher on 23/08/2017.
 */
class ArrayMaxSizeSpec extends GormDatastoreSpec {

    void "test size validation"() {

        given:
        MappingContext context = session.datastore.mappingContext
        context.setValidatorRegistry(new DefaultValidatorRegistry(context, session.datastore.getConnectionSources().getDefaultConnectionSource().settings))
        ArrayEntity invalid = new ArrayEntity(field: "foo", bytes: new byte[0], stringArray: new String[0])

        when:
        invalid.validate()

        then:
        invalid.hasErrors()
        invalid.errors.getFieldError('bytes')
        invalid.errors.getFieldError('stringArray')

    }
    @Override
    List getDomainClasses() {
        [ArrayEntity]
    }
}

@Entity
class ArrayEntity {

    String field
    byte[] bytes
    String[] stringArray

    static constraints = {
        bytes(minSize: 1, maxSize: 1024 * 1024 * 10)
        stringArray(minSize: 2, maxSize: 3)
    }
}