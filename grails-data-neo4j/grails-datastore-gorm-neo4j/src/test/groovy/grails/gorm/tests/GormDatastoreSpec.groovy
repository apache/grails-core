package grails.gorm.tests

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.gorm.validation.PersistentEntityValidator
import org.grails.datastore.gorm.events.ConfigurableApplicationContextEventPublisher
import org.grails.datastore.gorm.neo4j.config.Settings
import org.grails.datastore.gorm.neo4j.Neo4jDatastore
import org.grails.datastore.gorm.neo4j.Neo4jSession
import org.grails.datastore.gorm.validation.constraints.eval.DefaultConstraintEvaluator
import org.grails.datastore.gorm.validation.constraints.registry.DefaultConstraintRegistry
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.neo4j.driver.Driver
import org.neo4j.harness.ServerControls
import org.springframework.context.support.GenericApplicationContext
import org.springframework.context.support.StaticMessageSource
import org.springframework.validation.Validator
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
/**
 * Created by graemerocher on 06/06/16.
 */
abstract class GormDatastoreSpec extends Specification {

    List getDomainClasses() {
        [       Book, ChildEntity, City, ClassWithListArgBeforeValidate, ClassWithNoArgBeforeValidate,
                ClassWithOverloadedBeforeValidate, CommonTypes, Country, EnumThing, Face, Highway,
                Location, ModifyPerson, Nose, OptLockNotVersioned, OptLockVersioned, Person, PersonEvent,
                Pet, PetType, Plant, PlantCategory, Publication, Task, TestEntity]
    }

    @Shared @AutoCleanup Neo4jDatastore neo4jDatastore
    @Shared ServerControls serverControls
    @Shared Driver boltDriver
    @Shared GrailsApplication grailsApplication
    @Shared MappingContext mappingContext
    Neo4jSession session

    void setupSpec() {
        def ctx = new GenericApplicationContext()
        ctx.refresh()
        def allClasses = getDomainClasses() as Class[]


        neo4jDatastore = new Neo4jDatastore(
                [(Settings.SETTING_NEO4J_TYPE)                 : Settings.DATABASE_TYPE_EMBEDDED,
                 (Settings.SETTING_NEO4J_EMBEDDED_EPHEMERAL)   : true,
                 'grails.neo4j.embedded.options.dbms.shell':'true'] << getConfiguration(),
                new ConfigurableApplicationContextEventPublisher(ctx),
                allClasses
        )
        serverControls = (ServerControls)neo4jDatastore.connectionSources.defaultConnectionSource.serverControls
        boltDriver = neo4jDatastore.boltDriver
        mappingContext = neo4jDatastore.mappingContext

        grailsApplication = new DefaultGrailsApplication(allClasses, getClass().getClassLoader())
        grailsApplication.mainContext = ctx
        grailsApplication.initialise()
    }

    void setupValidator(Class entityClass, Validator validator = null) {
        PersistentEntity entity = mappingContext.persistentEntities.find { PersistentEntity e -> e.javaClass == entityClass }
        if (entity) {
            def messageSource = new StaticMessageSource()
            def evaluator = new DefaultConstraintEvaluator(new DefaultConstraintRegistry(messageSource), mappingContext, Collections.emptyMap())
            mappingContext.addEntityValidator(entity, validator ?:
                    new PersistentEntityValidator(entity, messageSource, evaluator)
                    )
        }
    }

    void setup() {
        session = neo4jDatastore.connect()
        DatastoreUtils.bindSession session
        session.beginTransaction()
    }

    void cleanup() {
        session.disconnect()
        DatastoreUtils.unbindSession(session)

        def session = boltDriver.session()
        def tx = session.beginTransaction()
        try {
            tx.run("MATCH (n) DETACH DELETE n")
            tx.commit()
        } finally {
            try {
                session.close()
            } catch (e) {
                // latest driver throws a nonsensical error. Ignore it for the moment
                if (!e.message.contains("insanely frequent schema changes")) {
                    throw e
                }
            }
        }
    }

    Map getConfiguration() {
        [:]
    }

}
