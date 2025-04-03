package grails.validation

import grails.util.ClosureToMapPopulator
import grails.util.Holders
import groovy.transform.Generated
import org.grails.core.support.GrailsApplicationDiscoveryStrategy
import org.grails.datastore.gorm.validation.constraints.eval.DefaultConstraintEvaluator
import org.springframework.context.support.GenericApplicationContext
import org.springframework.validation.FieldError
import spock.lang.Issue
import spock.lang.Specification

import java.lang.reflect.Method

/**
 * Ensure validation logic for command object with {@code Validateable} and whether or not compatible with domain class.
 *
 */
class ValidateableTraitSpec extends Specification {

    void 'Test validate can be invoked in a unit test with no special configuration'() {
        when: 'an object is valid'
        def validateable = new MyValidateable(name: 'Kirk', age: 47, town: 'STL')

        then: 'validate() returns true and there are no errors'
        validateable.validate()
        !validateable.hasErrors()
        validateable.errors.errorCount == 0

        when: 'an object is invalid'
        validateable.name = 'kirk'

        then: 'validate() returns false and the appropriate error is created'
        !validateable.validate()
        validateable.hasErrors()
        validateable.errors.errorCount == 1
        validateable.errors.getFieldError('name').code == 'matches.invalid'

        when: 'the clearErrors() is called'
        validateable.clearErrors()

        then: 'the errors are gone'
        !validateable.hasErrors()
        validateable.errors.errorCount == 0

        when: 'the object is put back in a valid state'
        validateable.name = 'Kirk'

        then: 'validate() returns true and there are no errors'
        validateable.validate()
        !validateable.hasErrors()
        validateable.errors.errorCount == 0
    }

    @Issue('GRAILS-10871')
    void 'Test that binding failures are retained during validation and that the corresponding property is not validated'() {
        given:
        def validateable = new MyValidateable()

        when:
        def fieldError = new FieldError(MyValidateable.name, 'age', 'type mismatched', true, null, null, null)
        validateable.errors.addError fieldError

        then:
        validateable.hasErrors()
        validateable.errors.errorCount == 1
        validateable.errors.getFieldError('age').rejectedValue == 'type mismatched'

        when:
        validateable.name = 'lower case'
        validateable.age = -1  // invalid value
        validateable.town = ''

        then:
        !validateable.validate()
        validateable.hasErrors()
        validateable.errors.errorCount == 2
        validateable.errors.getFieldError('age').rejectedValue == 'type mismatched'
        validateable.errors.getFieldError('name').rejectedValue == 'lower case'
    }

    void 'Test that validation failures are not retained during validation'() {
        given:
        def validateable = new MyValidateable()

        when:
        def fieldError = new FieldError(MyValidateable.name, 'age', 'any validation failure', false, null, null, null)
        validateable.errors.addError fieldError

        then:
        validateable.hasErrors()
        validateable.errors.errorCount == 1
        validateable.errors.getFieldError('age').rejectedValue == 'any validation failure'

        when:
        validateable.name = 'lower case'
        validateable.age = -1  // invalid value
        validateable.town = ''

        then:
        !validateable.validate()
        validateable.hasErrors()
        validateable.errors.errorCount == 2
        validateable.errors.getFieldError('age')?.rejectedValue == -1
        validateable.errors.getFieldError('name').rejectedValue == 'lower case'

        when:
        validateable.age = 1  // valid value

        then:
        !validateable.validate()
        validateable.hasErrors()
        validateable.errors.errorCount == 1
        validateable.errors.getFieldError('name').rejectedValue == 'lower case'
    }

    @Issue('GRAILS-11601')
    void 'Test that only the expected properties are constrained'() {
        when:
        def constraints = MyValidateable.getConstraintsMap()

        then:
        constraints.size() == 5
        constraints.containsKey 'name'
        constraints.containsKey 'town'
        constraints.containsKey 'age'
        constraints.containsKey 'someProperty'
        constraints.containsKey 'twiceAge' // only getter method

        and:
        constraints.name.appliedConstraints.size() == 2
        constraints.age.appliedConstraints.size() == 2
        constraints.town.appliedConstraints.size() == 1
        constraints.someProperty.appliedConstraints.size() == 1
        constraints.twiceAge.appliedConstraints.size() == 1

        and:
        constraints.name.hasAppliedConstraint 'matches'
        constraints.name.hasAppliedConstraint 'nullable'
        constraints.age.hasAppliedConstraint 'range'
        constraints.age.hasAppliedConstraint 'nullable'
        constraints.town.hasAppliedConstraint 'nullable'
        constraints.someProperty.hasAppliedConstraint 'nullable'
        constraints.twiceAge.hasAppliedConstraint 'nullable'

        and: 'implicit defaultNullable is nullable:false'
        !constraints.name.nullable
        !constraints.age.nullable
        !constraints.town.nullable
        !constraints.someProperty.nullable
        !constraints.twiceAge.nullable
    }

    void 'Test that constraints are nullable by default if overridden and ensure nullable:true constraint is not applied when no other constraints were defined by user'() {
        when:
        def constraints = MyNullableValidateable.getConstraintsMap()

        then: 'not including "name" because there is no constraints by user'
        constraints.size() == 3
        constraints.containsKey 'town'
        constraints.containsKey 'age'
        constraints.containsKey 'country'

        and: 'explicit nullable constraints are correctly applied'
        constraints.town.appliedConstraints.size() == 1
        !constraints.town.nullable
        constraints.country.appliedConstraints.size() == 1
        constraints.country.nullable

        and: 'if property has any constraint defined, nullable is added too'
        constraints.age.appliedConstraints.size() == 2
        constraints.age.nullable
    }

    @Issue('GRAILS-11625')
    void 'Test that properties defined in a class with overridden defaultNullable which are not explicitly constrained are not accessed during validation'() {
        given: 'an instance of a class with overridden defaultNullable returning true'
        def obj = new MyNullableValidateable(town: 'St. Louis', age: 18)

        expect: 'property accessors are not invoked for properties which are not explicitly constrained (getName() would throw an exception)'
        obj.validate()
    }

    void 'Ensure class without any constraints can be validated'() {
        given:
        NoConstraintsValidateable obj = new NoConstraintsValidateable()

        expect:
        obj.validate()
        !obj.hasErrors()
        obj.errors != null
    }

    @Issue('9513')
    void 'Ensure validation may be done for classes with private and protected getters'() {
        given:
        ValidateableGetters obj = new ValidateableGetters()

        expect: 'validation is executed and public properties/getters are marked nullable in errors'
        !obj.validate()
        obj.hasErrors()
        obj.errors.errorCount == 2
        obj.errors['town']?.code == 'nullable' // public property
        obj.errors['name']?.code == 'nullable' // only public getter method
        !obj.errors['surname'] // only protected getter method
        !obj.errors['email'] // only private getter method
    }

    void 'Ensure private and protected getter is not handled as not-nullable property by default'() {
        when:
        Map constraints = new ValidateableGetters().getConstraintsMap()

        then: 'only public properties and public getters should be considered validateable properties by default'
        constraints.size() == 2
        constraints.name
        constraints.town
    }

    void "Test that default and shared constraints can be applied from configuration"() {
        given:
        GenericApplicationContext applicationContext = new GenericApplicationContext()
        applicationContext.refresh()
        def defaultConstraints = new ClosureToMapPopulator().populate {
            '*' blank: false
            myShared matches: /MY_SHARED/, maxSize: 10
        }
        applicationContext.beanFactory.registerSingleton(
                "constraintEvaluator",
                new DefaultConstraintEvaluator(defaultConstraints)
        )

        def strategy = Mock(GrailsApplicationDiscoveryStrategy)
        strategy.findApplicationContext() >> applicationContext
        Holders.addApplicationDiscoveryStrategy(strategy)

        and:
        def constraints = SharedConstraintsValidateable.getConstraintsMap()

        expect:
        constraints.size() == 2
        constraints.name.hasAppliedConstraint 'matches'
        constraints.town.hasAppliedConstraint 'inList'

        and: 'default nullable'
        constraints.name.hasAppliedConstraint 'nullable'
        constraints.town.hasAppliedConstraint 'nullable'

        and: 'default constraints "*"'
        constraints.name.hasAppliedConstraint 'blank'
        constraints.town.hasAppliedConstraint 'blank'

        and: 'shared constraints'
        constraints.town.hasAppliedConstraint 'matches'
        constraints.town.hasAppliedConstraint 'maxSize'

        cleanup:
        Holders.clear()
    }

    void 'Ensure properties of super class is inherited'() {
        when:
        Map constraints = new SubClassValidateable().getConstraintsMap()

        then:
        constraints.size() == 2
        constraints.superName.appliedConstraints.size() == 2
        constraints.superName.hasAppliedConstraint 'matches'
        constraints.superName.hasAppliedConstraint 'nullable'
        constraints.subName.appliedConstraints.size() == 2
        constraints.subName.hasAppliedConstraint 'matches'
        constraints.subName.hasAppliedConstraint 'nullable'
    }

    void 'Ensure properties of super class can be validated'() {
        given:
        def validateable = new SubClassValidateable()

        expect:
        !validateable.validate()
        validateable.hasErrors()
        validateable.errors.errorCount == 2
        validateable.errors['superName']?.code == 'nullable'
        validateable.errors['subName']?.code == 'nullable'
    }

    @Issue('apache/grails-core#9774')
    void 'test a Java class which references a Groovy class marked with @Validateable'() {
        given:
        def obj = new SomeJavaClass()

        when:
        obj.someValidateable = new MyValidateable(name: 'jeff')

        then:
        !obj.someValidateable.validate(['name'])

        when:
        obj.someValidateable = new MyValidateable(name: 'Jeff')

        then:
        obj.someValidateable.validate(['name'])
    }

    void "test that all Validateable trait methods are marked as Generated"() {
        expect: "all Validateable methods are marked as Generated on implementation class"
        Validateable.getMethods().each { Method traitMethod ->
            assert TestGeneratedAnnotations.class.getMethod(traitMethod.name, traitMethod.parameterTypes).isAnnotationPresent(Generated)
        }
    }
}

class MyValidateable implements Validateable {
    String name
    Integer age
    String town
    private String _someProperty = 'default value'

    void setSomeOtherProperty(String s) {}

    void setSomeProperty(String s) {
        _someProperty = s
    }

    String getSomeProperty() {
        _someProperty
    }

    int getTwiceAge() {
        age * 2
    }

    static constraints = {
        name matches: /[A-Z].*/
        age range: 1..99
    }
}

class MyNullableValidateable implements Validateable {

    Integer age
    String town
    String country

    @SuppressWarnings('GrMethodMayBeStatic')
    String getName() {
        throw new UnsupportedOperationException('getName() should not have been called during validation')
    }

    static constraints = {
        town nullable: false
        age validator: { val -> val > 0 }
        country nullable: true
    }

    static boolean defaultNullable() {
        true
    }
}

class NoConstraintsValidateable implements Validateable {
    static constraints = {

    }
}

class ValidateableGetters implements Validateable {
    //standard properties - private/protected will not have getter
    String town

    //properties from getters
    String getName() {}

    protected String getSurname() {}

    private String getEmail() {}

    //static properties
    static Integer day

    //static properties from getters
    static Date getEaster() {}

    protected static Date getChristmas() {}

    private static Date getNewYear() {}
}

class SharedConstraintsValidateable implements Validateable {
    String name
    String town

    static constraints = {
        name matches: /[A-Z].*/
        town shared: "myShared", inList: ['St. Louis']
    }
}

class SuperClassValidateable {
    String superName

    static constraints = {
        superName matches: /SUPER/
    }
}

class SubClassValidateable extends SuperClassValidateable implements Validateable {
    String subName

    static constraints = {
        subName matches: /SUB/
    }
}

class TestGeneratedAnnotations implements Validateable {

}