package grails.mongodb.bootstrap

import com.mongodb.client.MongoClient
import grails.mongodb.MongoEntity
import grails.mongodb.geo.Point
import grails.persistence.Entity
import org.bson.Document
import org.grails.datastore.gorm.mongo.Birthday
import org.grails.datastore.gorm.mongo.BirthdayCodec
import org.grails.datastore.mapping.engine.types.AbstractMappingAwareCustomTypeMarshaller
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoMappingContext
import org.grails.datastore.mapping.query.Query
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.Specification

/**
 * @author Graeme Rocher
 */
class MongoDbDataStoreSpringInitializerSpec extends Specification{

    void "Test that MongoDbDatastoreSpringInitializer can setup GORM for MongoDB from scratch"() {
        when:"the initializer used to setup GORM for MongoDB"
            def initializer = new MongoDbDataStoreSpringInitializer(Person)
            def applicationContext = initializer.configure()
            def mongo = applicationContext.getBean(MongoClient)
            mongo.getDatabase(MongoDbDataStoreSpringInitializer.DEFAULT_DATABASE_NAME).drop()

        then:"GORM for MongoDB is initialized correctly"
            Person.count() == 0

    }

    void "Test specify mongo database name settings"() {
        when:"the initializer used to setup GORM for MongoDB"
        def initializer = new MongoDbDataStoreSpringInitializer(['grails.mongodb.databaseName':'foo'],Person)
        def applicationContext = initializer.configure()
        def mongoDatastore = applicationContext.getBean(MongoDatastore)

        then:"GORM for MongoDB is initialized correctly"
        mongoDatastore.getDefaultDatabase() == 'foo'

        cleanup:
        mongoDatastore.destroy()
    }

    void "Test the alias is created when it is the primary datastore"() {
        when:"the initializer used to setup GORM for MongoDB"
        def initializer = new MongoDbDataStoreSpringInitializer(['grails.mongodb.databaseName':'foo'],Person)
        def applicationContext = initializer.configure()
        def mongoDatastore = applicationContext.getBean(MongoDatastore)

        then:
        applicationContext.containsBean("grailsDomainClassMappingContext")

        cleanup:
        mongoDatastore.destroy()
    }

    void "Test the alias is not created when it is the secondary datastore"() {
        when:"the initializer used to setup GORM for MongoDB"
        def initializer = new MongoDbDataStoreSpringInitializer(['grails.mongodb.databaseName':'foo'],Person)
        initializer.setSecondaryDatastore(true)
        def applicationContext = initializer.configure()
        def mongoDatastore = applicationContext.getBean(MongoDatastore)

        then:
        !applicationContext.containsBean("grailsDomainClassMappingContext")

        cleanup:
        mongoDatastore.destroy()
    }

    @Issue('GPMONGODB-339')
    @Ignore // The MongoDB API for this test has been altered / removed with no apparent replacement for getting the number of pooled connections in use
    void "Test withTransaction returns connections when used without session handling"() {
        given:"the initializer used to setup GORM for MongoDB"
            def initializer = new MongoDbDataStoreSpringInitializer(Person)
            def applicationContext = initializer.configure()
            def mongo = applicationContext.getBean(Mongo)

        when:"The a normal GORM method is used"
            Person.count()
        then:"No connections are in use afterwards"
            db.getStats().get("connections") == 0
            mongo.connector.@_masterPortPool.statistics.inUse == 0

        when:"The withTransaction method is used"
            Person.withTransaction {
                new Person(name:"Bob").save()
            }

        then:"No connections in use"
            mongo.connector.@_masterPortPool.statistics.inUse == 0
    }

    void "Test that constraints and Geo types work"() {
        given:"the initializer used to setup GORM for MongoDB"
            def initializer = new MongoDbDataStoreSpringInitializer(Person)
            initializer.configure()
            Person.DB.drop()

        when:"we try to persist an invalid object"
            def p = new Person().save(flush:true)

        then:"The object is null and not persisted"
            p == null
            Person.count() == 0

        when:"We persist a Geo type"
            Person.withNewSession {
                new Person(name: "Bob", home: Point.valueOf(10, 10)).save(flush:true)
                p = Person.first()
            }

        then:"The geo type was persisted"
            p != null
            p.home != null

    }

    void "Test custom codecs from Spring"() {
        given:"the initializer used to setup GORM for MongoDB"
        def initializer = new MongoDbDataStoreSpringInitializer(Person)
        AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext()
        applicationContext.beanFactory.registerSingleton("birthdayCodec", new BirthdayCodec())

        initializer.configureForBeanDefinitionRegistry(applicationContext)
        applicationContext.refresh()
        Person.DB.drop()

        when:"we persist an object with a custom type "
        def birthday = new Birthday(new Date())
        def p = new Person(name: "Bob", home: Point.valueOf(10, 10), birthday: birthday).save(flush:true)

        then:"The object was persisted successfully"
        Person.findByBirthday(birthday).birthday == birthday
        !Person.findByBirthday(new Birthday(new Date() - 7))
    }

    void "Test custom type marshallers from Spring"() {
        given:"the initializer used to setup GORM for MongoDB"
        def initializer = new MongoDbDataStoreSpringInitializer(Person)
        AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext()
        applicationContext.beanFactory.registerSingleton("birthdayMarshaller", new BirthdayCustomTypeMarshaller())

        initializer.configureForBeanDefinitionRegistry(applicationContext)
        applicationContext.refresh()
        Person.DB.drop()

        when:"we persist an object with a custom type "
        def birthday = new Birthday(new Date())
        new Person(name: "Bob", home: Point.valueOf(10, 10), birthday: birthday).save(flush:true)

        then:"The object was persisted successfully"
        Person.first().birthday == birthday
        Person.findByBirthday(birthday).birthday == birthday
        !Person.findByBirthday(new Birthday(new Date() - 7))
    }
}


@Entity
class Person implements MongoEntity<Person> {
    Long id
    Long version
    String name
    Point home
    Birthday birthday

    static constraints = {
        name blank:false
        birthday nullable: true
    }
}

class BirthdayCustomTypeMarshaller extends AbstractMappingAwareCustomTypeMarshaller<Birthday, Document, Document> {

    BirthdayCustomTypeMarshaller() {
        super(Birthday)
    }

    @Override
    boolean supports(MappingContext context) {
        return context instanceof MongoMappingContext
    }

    @Override
    protected Object writeInternal(PersistentProperty property, String key, Birthday value, Document nativeTarget) {
        nativeTarget.put(key, value.date)
        return nativeTarget
    }

    @Override
    protected Birthday readInternal(PersistentProperty property, String key, Document nativeSource) {
        return new Birthday(nativeSource.getDate(key))
    }

    @Override
    protected void queryInternal(PersistentProperty property, String key, Query.PropertyCriterion value, Document nativeQuery) {
        nativeQuery.put(key, value.getValue().date)
    }
}