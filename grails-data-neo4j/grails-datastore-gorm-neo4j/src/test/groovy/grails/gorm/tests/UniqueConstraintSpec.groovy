package grails.gorm.tests

import grails.gorm.annotation.Entity

/**
 * Created by graemerocher on 13/12/16.
 */

import org.grails.datastore.gorm.validation.constraints.builtin.UniqueConstraint
import org.grails.datastore.gorm.validation.constraints.eval.DefaultConstraintEvaluator
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.config.GormProperties
import org.springframework.validation.Errors
import org.springframework.validation.Validator
import spock.lang.Issue

import javax.persistence.FlushModeType

/**
 * Tests the unique constraint
 */
class UniqueConstraintSpec extends GormDatastoreSpec {

    @Issue('https://github.com/grails/grails-core/issues/9596')
    void "Test update secondary property when using unique constraint"() {
        when:"An object with a unique constraint is saved"
        UniqueGroup o = new UniqueGroup(name: "foo", desc: "foo description").save(flush: true)


        then:"The object is saved"
        o.id != null
        o.name == 'foo'
        o.desc == 'foo description'

        when:"A secondary property is updated"
        session.clear()
        o = UniqueGroup.findByName("foo")
        o.desc = 'description changed'
        o.save(flush:true)
        session.clear()
        o = UniqueGroup.findByName("foo")

        then:"The object was saved"
        o != null
        o.name == 'foo'
        o.desc == 'description changed'
    }

    void "Test simple unique constraint"() {
        when:"Two domain classes with the same name are saved"
        def one = new UniqueGroup(name:"foo").save(flush:true, failOnError:true)
        def two = new UniqueGroup(name:"foo")
        two.save(flush:true)

        then:"The second has errors"
        one != null
        two.hasErrors()
        UniqueGroup.count() == 1

        when:"The first is saved again"
        one = one.save(flush:true)

        then:"The are no errors"
        one != null

        when:"Three domain classes are saved within different uniqueness groups"
        one = new GroupWithin(name:"foo", org:"mycompany").save(flush:true, failOnError:true)
        two = new GroupWithin(name:"foo", org:"othercompany").save(flush:true, failOnError:true)
        def three = new GroupWithin(name:"foo", org:"mycompany")
        three.save(flush:true)

        then:"Only the third has errors"
        one != null
        two != null
        GroupWithin.count() == 2
        three.hasErrors()
    }

    void "should update to a existing value fail"() {
        given:"A validator that uses the unique constraint"

        new UniqueGroup(name:"foo").save()
        def two = new UniqueGroup(name:"bar").save()

        session.flush()
        session.clear()

        when:
        two.name="foo"
        two.save(flush:true)

        then:
        two.hasErrors()
        UniqueGroup.count() == 2
        UniqueGroup.get(two.id).name=="bar"

        when:
        session.clear()
        two = UniqueGroup.get(two.id)

        then:
        two.name == "bar"

    }

    @Override
    List getDomainClasses() {
        [UniqueGroup, GroupWithin]
    }
}

@Entity
class UniqueGroup implements Serializable, DirtyCheckable {
    Long id
    Long version
    String name
    String desc
    static constraints = {
        name unique:true, index:true
        desc nullable: true
    }
}

@Entity
class GroupWithin implements Serializable {
    Long id
    Long version
    String name
    String org
    static constraints = {
        name unique:"org", index:true
        org index:true
    }
}
