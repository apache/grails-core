
package org.grails.datastore.gorm

import com.mongodb.BasicDBObject
import grails.gorm.tests.GormDatastoreSpec
import org.bson.Document
import org.grails.datastore.bson.query.BsonQuery
import org.grails.datastore.gorm.mongo.Birthday
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.engine.types.AbstractMappingAwareCustomTypeMarshaller
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.mongo.AbstractMongoSession
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoSettings
import org.grails.datastore.mapping.query.Query.Between
import org.grails.datastore.mapping.query.Query.PropertyCriterion
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.util.StringUtils
import org.springframework.validation.Errors
import org.springframework.validation.Validator

/**
 * @author graemerocher
 */
class Setup {

    static MongoDatastore mongo
    static AbstractMongoSession session

    static destroy() {
        session.nativeInterface.dropDatabase( session.defaultDatabase )
        session.disconnect()
        TransactionSynchronizationManager.unbindResource(mongo)
        mongo.close()
    }

    static Session setup(classes) {
        def databaseName = System.getProperty(GormDatastoreSpec.CURRENT_TEST_NAME) ?: 'test'

        Map<String,Object> config = [(MongoSettings.SETTING_DATABASE_NAME): databaseName]

        // disable decimal type support on Travis, since MongoDB 3.4 support doesn't exist there yet
        if(System.getenv('TRAVIS')) {
            config.put(MongoSettings.SETTING_DECIMAL_TYPE, false)
        }
        mongo = new MongoDatastore(config, classes as Class[])
        mongo.mappingContext.mappingFactory.registerCustomType(new AbstractMappingAwareCustomTypeMarshaller<Birthday, Document, Document>(Birthday) {
            @Override
            protected Object writeInternal(PersistentProperty property, String key, Birthday value, Document nativeTarget) {
                final converted = value.date.time
                nativeTarget.put(key, converted)
                return converted
            }

            @Override
            protected void queryInternal(PersistentProperty property, String key, PropertyCriterion criterion, Document nativeQuery) {
                if (criterion instanceof Between) {
                    def dbo = new BasicDBObject()
                    dbo.put(BsonQuery.MONGO_GTE_OPERATOR, criterion.getFrom().date.time)
                    dbo.put(BsonQuery.MONGO_LTE_OPERATOR, criterion.getTo().date.time)
                    nativeQuery.put(key, dbo)
                }
                else {
                    nativeQuery.put(key, criterion.value.date.time)
                }
            }

            @Override
            protected Birthday readInternal(PersistentProperty property, String key, Document nativeSource) {
                final num = nativeSource.get(key)
                if (num instanceof Long) {
                    return new Birthday(new Date(num))
                }
                return null
            }
        })


        PersistentEntity entity = mongo.mappingContext.persistentEntities.find { PersistentEntity e -> e.name.contains("TestEntity")}

        mongo.mappingContext.addEntityValidator(entity, [
            supports: { Class c -> true },
            validate: { Object o, Errors errors ->
                if (!StringUtils.hasText(o.name)) {
                    errors.rejectValue("name", "name.is.blank")
                }
            }
        ] as Validator)



        session = mongo.connect()

        TransactionSynchronizationManager.bindResource(mongo, session)

        return session
    }
}
