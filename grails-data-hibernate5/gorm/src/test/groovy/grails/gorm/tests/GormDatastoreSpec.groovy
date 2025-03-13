package grails.gorm.tests

import grails.gorm.tck.Book
import grails.gorm.tck.ChildEntity
import grails.gorm.tck.City
import grails.gorm.tck.ClassWithListArgBeforeValidate
import grails.gorm.tck.ClassWithNoArgBeforeValidate
import grails.gorm.tck.ClassWithOverloadedBeforeValidate
import grails.gorm.tck.CommonTypes
import grails.gorm.tck.Country
import grails.gorm.tck.EnumThing
import grails.gorm.tck.Face
import grails.gorm.tck.Highway
import grails.gorm.tck.Location
import grails.gorm.tck.ModifyPerson
import grails.gorm.tck.Nose
import grails.gorm.tck.OptLockNotVersioned
import grails.gorm.tck.OptLockVersioned
import grails.gorm.tck.Person
import grails.gorm.tck.PersonEvent
import grails.gorm.tck.Pet
import grails.gorm.tck.PetType
import grails.gorm.tck.Plant
import grails.gorm.tck.PlantCategory
import grails.gorm.tck.Publication
import grails.gorm.tck.Task
import grails.gorm.tck.TestEntity
import groovy.util.logging.Slf4j
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.Session
import spock.lang.Shared
import spock.lang.Specification

/**
 * A Spec base class that manages a Session for each feature as well as
 * meta class cleanup on the Entity classes in the TCK.
 *
 * Users of this class need to provide a "setup" class at runtime that
 * provides the session instance. It *must* have the following name:
 *
 * - org.grails.datastore.gorm.Setup
 *
 * This class must contain a static no-arg method called "setup()"
 * that returns a Session instance.
 */
@Slf4j
abstract class GormDatastoreSpec extends Specification {

    static final CURRENT_TEST_NAME = "current.gorm.test"
    static final SETUP_CLASS_NAME = 'org.grails.datastore.gorm.Setup'
    static final TEST_CLASSES = [
            Book, ChildEntity, City, ClassWithListArgBeforeValidate, ClassWithNoArgBeforeValidate,
            ClassWithOverloadedBeforeValidate, CommonTypes, Country, EnumThing, Face, Highway,
            Location, ModifyPerson, Nose, OptLockNotVersioned, OptLockVersioned, Person, PersonEvent,
            Pet, PetType, Plant, PlantCategory, Publication, Task, TestEntity]

    @Shared Class setupClass

    Session session

    def setupSpec() {
        setupClass = loadSetupClass()
    }

    def setup() {
        log.info('Using hibernate5 datastore class')
        cleanRegistry()
        System.setProperty(CURRENT_TEST_NAME, this.getClass().simpleName - 'Spec')
        session = createSession()
        DatastoreUtils.bindSession session
    }

    Session createSession() {
        setupClass.setup(((TEST_CLASSES + getDomainClasses()) as Set) as List)
    }

    List getDomainClasses() {
        []
    }

    def cleanup() {
        if (session) {
            session.disconnect()
            DatastoreUtils.unbindSession session
        }
        try {
            setupClass.destroy()
        } catch(e) {
            println "ERROR: Exception during test cleanup: ${e.message}"
        }

        cleanRegistry()
    }

    private cleanRegistry() {
        for (clazz in (TEST_CLASSES + getDomainClasses() )) {
            GroovySystem.metaClassRegistry.removeMetaClass(clazz)
        }
    }

    static Class loadSetupClass() {
        try {
            getClassLoader().loadClass(SETUP_CLASS_NAME)
        } catch (Throwable e) {
            throw new RuntimeException("Datastore setup class ($SETUP_CLASS_NAME) was not found",e)
        }
    }
}
