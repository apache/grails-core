package org.grails.orm.hibernate.cfg

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.engine.types.AbstractMappingAwareCustomTypeMarshaller
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.ValueGenerator
import org.grails.orm.hibernate.connections.HibernateConnectionSourceSettings
import spock.lang.Specification

/**
 * Created by graemerocher on 07/10/2016.
 */
class HibernateMappingContextSpec extends Specification {

    void "test entity with custom id generator"() {
        when:"A context is created"
        def mappingContext = new HibernateMappingContext()
        PersistentEntity entity = mappingContext.addPersistentEntity(CustomIdGeneratorEntity)

        then:"The mapping is correct"
        entity.mapping.identifier.generator == ValueGenerator.CUSTOM
    }

    void "test entity with custom type marshaller is registered correctly"() {
        given:"A configured custom type marshaller"
        HibernateConnectionSourceSettings settings = new HibernateConnectionSourceSettings()
        settings.custom.types = [new MyTypeMarshaller(MyUUIDGenerator)]

        when:"A context is created"
        def mappingContext = new HibernateMappingContext(settings)

        then:"The mapping is created successfully"
        mappingContext
        mappingContext.mappingFactory

        and:"The type is registered as a custom type with the mapping factory"
        mappingContext.mappingFactory.isCustomType(MyUUIDGenerator)
    }
}

@Entity
class CustomIdGeneratorEntity {
    String name
    static mapping = {
        id(generator: "org.grails.orm.hibernate.cfg.MyUUIDGenerator", type: "uuid-binary")
    }
}

class MyUUIDGenerator {
}

class MyTypeMarshaller extends AbstractMappingAwareCustomTypeMarshaller {
    MyTypeMarshaller(Class targetType) {
        super(targetType)
    }
    @Override
    protected Object writeInternal(PersistentProperty property, String key, Object value, Object nativeTarget) {
        return value
    }
    @Override
    protected Object readInternal(PersistentProperty property, String key, Object nativeSource) {
        return nativeSource
    }
}