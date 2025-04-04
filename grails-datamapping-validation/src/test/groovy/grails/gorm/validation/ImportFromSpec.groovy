package grails.gorm.validation

import groovy.transform.CompileStatic
import org.grails.datastore.gorm.validation.constraints.registry.DefaultValidatorRegistry
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.validation.ValidatorRegistry
import spock.lang.Specification

import jakarta.persistence.Entity

class ImportFromSpec extends Specification {

    void "test gets the metaConstraints"() {
        given:"setup validator registry"
        MappingContext mappingContext = new KeyValueMappingContext("test")
        def entity = mappingContext.addPersistentEntity(ImportFromTestEntity)
        ValidatorRegistry registry = new DefaultValidatorRegistry(mappingContext, new ConnectionSourceSettings())

        expect:"The validator is correct"
        def validator = (PersistentEntityValidator)registry.getValidator(entity)
        validator.constrainedProperties['createdDay'].metaConstraints["bindable"] == false
        validator.constrainedProperties['createdDay'].metaConstraints["example"] == "2017-12-31"
    }

}

@Entity
class ImportFromTestEntity implements DayStamp {
    String name

    static constraints = {
        importFrom(DayStampConstraints)
    }
}

@CompileStatic
trait DayStamp {

    Date createdDay

}

class DayStampConstraints implements DayStamp {

    static constraints = {
        createdDay nullable: true, bindable: false, example: "2017-12-31"
    }
}



