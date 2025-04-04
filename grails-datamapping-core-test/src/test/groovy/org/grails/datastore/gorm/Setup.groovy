package org.grails.datastore.gorm

import org.grails.datastore.gorm.events.AutoTimestampEventListener
import org.grails.datastore.gorm.events.DomainEventListener
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.engine.types.AbstractMappingAwareCustomTypeMarshaller
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.query.Query.Between
import org.grails.datastore.mapping.query.Query.PropertyCriterion
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import org.grails.datastore.mapping.simple.query.SimpleMapQuery
import org.grails.datastore.mapping.simple.query.SimpleMapResultList
import org.grails.datastore.mapping.transactions.DatastoreTransactionManager
import org.springframework.context.support.GenericApplicationContext
import org.springframework.util.StringUtils
import org.springframework.validation.Errors
import org.springframework.validation.Validator

/**
 * @author graemerocher
 */
class Setup {

    static destroy() {
        // noop
    }
    static Session setup(classes) {
        def ctx = new GenericApplicationContext()
        ctx.refresh()
        def simple = new SimpleMapDatastore(ctx)

        simple.mappingContext.mappingFactory.registerCustomType(new AbstractMappingAwareCustomTypeMarshaller<Birthday, Map, SimpleMapResultList>(Birthday) {
            @Override
            protected Object writeInternal(PersistentProperty property, String key, Birthday value, Map nativeTarget) {
                if (value == null) {
                    return null
                }

                final converted = value.date.time
                nativeTarget.put(key, converted)
                return converted
            }

            @Override
            protected void queryInternal(PersistentProperty property, String key, PropertyCriterion criterion, SimpleMapResultList nativeQuery) {
                SimpleMapQuery query = nativeQuery.query
                def handler = query.handlers[criterion.getClass()]

                if (criterion instanceof Between) {
                    criterion.from = criterion.from.date.time
                    criterion.to = criterion.to.date.time
                    nativeQuery.results << handler?.call(criterion, property) ?: []
                }
                else {
                    criterion.value = criterion.value.date.time
                    nativeQuery.results << handler?.call(criterion, property) ?: []
                }
            }

            @Override
            protected Birthday readInternal(PersistentProperty property, String key, Map nativeSource) {
                final num = nativeSource.get(key)
                if (num instanceof Long) {
                    return new Birthday(new Date(num))
                }
                return null
            }
        })
        for (cls in classes) {
            simple.mappingContext.addPersistentEntity(cls)
        }

        PersistentEntity entity = simple.mappingContext.persistentEntities.find {
            PersistentEntity e -> e.name.contains("TestEntity")}

        simple.mappingContext.addEntityValidator(entity, [
            supports: { Class c -> true },
            validate: { Object o, Errors errors ->
                if (!StringUtils.hasText(o.name)) {
                    errors.rejectValue("name", "name.is.blank")
                }
            }
        ] as Validator)


        simple.connect()
    }
}
