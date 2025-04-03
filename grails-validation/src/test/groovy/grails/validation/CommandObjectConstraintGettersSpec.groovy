package grails.validation

import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.Specification
import grails.validation.Validateable

/**
 * This is a test suite which should prevent issues from apache/grails-core#9749 and
 * apache/grails-core#9754 to happen again
 *
 * It verifies all different scenarios of getter/setter methods in command object
 * and Grails making them constrained properties in valid cases only
 *
 * Main assumptions are:
 * * public properties are by default constrained
 * * properties from methods are only constrained if are public, non static and have both getter and setter
 * * properties and methods inherited from super class or trait behave in the same way
 *
 * Analogous test case is created for Domain classes
 *
 * @see grails.validation.DomainConstraintGettersSpec
 */
@Issue(['apache/grails-core#9749', 'apache/grails-core#9754'])
class CommandObjectConstraintGettersSpec extends Specification {

    // STANDARD COMMAND OBJECT

    void 'ensure all public properties are by default constraint properties'() {
        SimplePropertiesCommand command = new SimplePropertiesCommand()

        when: 'empty command with simple properties is validated'
        command.validate()

        then: 'only public should fail on nullable constraint'
        command.hasErrors()
        command.errors['string']?.code == 'nullable'
        command.errors['book']?.code == 'nullable'
        command.errors.getErrorCount() == 2
    }

    void 'ensure constrained properties are only public ones'() {
        when: 'constrained properties map is get'
        Map constrainedProperties = SimplePropertiesCommand.getConstraintsMap()

        then: 'only 4 defined public properties are there'
        constrainedProperties.size() == 4
        constrainedProperties.containsKey('list')
        constrainedProperties.containsKey('map')
        constrainedProperties.containsKey('string')
        constrainedProperties.containsKey('book')
    }


    void 'ensure only public non-static properties with getter are constrained properties'() {
        MethodPropertiesCommand command = new MethodPropertiesCommand()

        when: 'empty command with method properties is validated'
        command.validate()

        //anotherPublicProperty did not error because it is boolean which defaults to false
        then: 'only public with getter should fail'
        command.hasErrors()
        command.errors['booleanPublicProperty']?.code == 'nullable'
        command.errors['getterOnly']?.code == 'nullable'
        command.errors.getErrorCount() == 2
    }


    void 'ensure constrained method properties are only public ones with both getter and setter'() {
        when: 'constrained properties map is get'
        Map constrainedProperties = MethodPropertiesCommand.getConstraintsMap()

        then: 'only public property with getter and setter should fail'
        constrainedProperties.size() == 3
        constrainedProperties.containsKey('anotherPublicProperty')
        constrainedProperties.containsKey('booleanPublicProperty')
        constrainedProperties.containsKey('getterOnly')
    }

    // COMMAND OBJECT WITH SUPER CLASS

    void 'ensure all inherited public properties are by default constraint properties'() {
        InheritedPropertiesCommand command = new InheritedPropertiesCommand()

        when: 'empty command with simple properties from parent class inheriteds validated'
        command.validate()

        then: 'all non collection public properties should fail on nullable constraint'
        command.hasErrors()
        command.errors['string']?.code == 'nullable'
        command.errors['book']?.code == 'nullable'
        command.errors.getErrorCount() == 2
    }

    void 'ensure inherited constrained properties are only public ones'() {
        when: 'constrained properties map is get on child class'
        Map constrainedProperties = InheritedPropertiesCommand.getConstraintsMap()

        then: 'only 4 defined public properties are there'
        constrainedProperties.size() == 4
        constrainedProperties.containsKey('list')
        constrainedProperties.containsKey('map')
        constrainedProperties.containsKey('string')
        constrainedProperties.containsKey('book')
    }


    void 'ensure only public non-static inherited properties with getter are constrained properties'() {
        InheritedMethodPropertiesCommand command = new InheritedMethodPropertiesCommand()
        when: 'empty command with method properties is validated'
        command.validate()

        //anotherPublicProperty did not error because it is boolean which defaults to false
        then: 'only public with getter and setter should fail'
        command.hasErrors()
        command.errors['booleanPublicProperty']?.code == 'nullable'
        command.errors['getterOnly']?.code == 'nullable'
        command.errors.getErrorCount() == 2
    }


    void 'ensure constrained inherited method properties are only public ones with getter'() {
        when: 'constrained properties map is get from child class'
        Map constrainedProperties = InheritedMethodPropertiesCommand.getConstraintsMap()

        then: 'only public with getter and setter should be there'
        constrainedProperties.size() == 3
        constrainedProperties.containsKey('anotherPublicProperty')
        constrainedProperties.containsKey('booleanPublicProperty')
        constrainedProperties.containsKey('getterOnly')
    }

    // COMMAND OBJECT WITH TRAIT

    void 'ensure all trait public properties are by default constraint properties'() {
        TraitPropertiesCommand command = new TraitPropertiesCommand()

        when: 'empty command with trait properties is validated'
        command.validate()

        then: 'only public should fail on nullable constraint'
        command.hasErrors()
        command.errors['string']?.code == 'nullable'
        command.errors['book']?.code == 'nullable'
        command.errors.getErrorCount() == 2
    }

    void 'ensure constrained properties are only traits public ones'() {
        when: 'constrained properties map is get'
        Map constrainedProperties = TraitPropertiesCommand.getConstraintsMap()

        then: 'only 4 defined public properties are there'
        constrainedProperties.size() == 4
        constrainedProperties.containsKey('list')
        constrainedProperties.containsKey('map')
        constrainedProperties.containsKey('string')
        constrainedProperties.containsKey('book')
    }


    void 'ensure only public non-static properties from trait with getter are constrained properties'() {
        TraitMethodPropertiesCommand command = new TraitMethodPropertiesCommand()
        when: 'empty command with simple properties is validated'
        command.validate()

        //anotherPublicProperty did not error because it is boolean which defaults to false
        then: 'all should fail on nullable constraint'
        command.hasErrors()
        command.errors['booleanPublicProperty']?.code == 'nullable'
        command.errors['getterOnly']?.code == 'nullable'
        command.errors.getErrorCount() == 2
    }


    void 'ensure constrained method properties from trait are only public ones with getter'() {
        when: 'constrained properties map is get'
        Map constrainedProperties = TraitMethodPropertiesCommand.getConstraintsMap()

        then: 'only 4 defined public properties are there'
        constrainedProperties.size() == 3
        constrainedProperties.containsKey('anotherPublicProperty')
        constrainedProperties.containsKey('booleanPublicProperty')
        constrainedProperties.containsKey('getterOnly')
    }

    // BOOL METHODS COMMAND OBJECT
    
    void 'ensure only public non-static bool properties with getter are constrained properties'() {
        BoolMethodPropertiesCommand command = new BoolMethodPropertiesCommand()
        when: 'empty command with method properties is validated'
        command.validate()

        //anotherPublicProperty did not error because it is boolean which defaults to false
        then: 'only public with getter and setter should fail'
        command.hasErrors()
        command.errors['booleanPublicProperty']?.code == 'nullable'
        command.errors['getterOnly']?.code == 'nullable'
        command.errors.getErrorCount() == 2
    }


    void 'ensure constrained bool method properties are only public ones with getter'() {
        when: 'constrained properties map is get'
        Map constrainedProperties = BoolMethodPropertiesCommand.getConstraintsMap()

        then: 'only public property with getter and setter should fail'
        constrainedProperties.size() == 3
        constrainedProperties.containsKey('anotherPublicProperty')
        constrainedProperties.containsKey('booleanPublicProperty')
        constrainedProperties.containsKey('getterOnly')
    }

    // BOOL COMMAND OBJECT WITH SUPER CLASS

    void 'ensure only public non-static inherited bool properties with getter are constrained properties'() {
        InheritedBoolMethodPropertiesCommand command = new InheritedBoolMethodPropertiesCommand()
        when: 'empty command with method properties is validated'
        command.validate()

        //anotherPublicProperty did not error because it is boolean which defaults to false
        then: 'only public with getter and setter should fail'
        command.hasErrors()
        command.errors['booleanPublicProperty']?.code == 'nullable'
        command.errors['getterOnly']?.code == 'nullable'
        command.errors.getErrorCount() == 2
    }

    void 'ensure constrained inherited bool method properties are only public ones with getter'() {
        when: 'constrained properties map is get from child class'
        Map constrainedProperties = InheritedBoolMethodPropertiesCommand.getConstraintsMap()

        then: 'only public with getter and setter should be there'
        constrainedProperties.size() == 3
        constrainedProperties.containsKey('anotherPublicProperty')
        constrainedProperties.containsKey('booleanPublicProperty')
        constrainedProperties.containsKey('getterOnly')
    }

    // BOOL COMMAND OBJECT WITH TRAIT


    void 'ensure only public non-static bool properties from trait with getter are constrained properties'() {
        TraitBoolMethodPropertiesCommand command = new TraitBoolMethodPropertiesCommand()
        when: 'empty command with simple properties is validated'
        command.validate()

        //anotherPublicProperty did not error because it is boolean which defaults to false
        then: 'all should fail on nullable constraint'
        command.hasErrors()
        command.errors['booleanPublicProperty']?.code == 'nullable'
        command.errors['getterOnly']?.code == 'nullable'
        command.errors.getErrorCount() == 2
    }

    void 'ensure constrained bool method properties from trait are only public ones with getter'() {
        when: 'constrained properties map is get'
        Map constrainedProperties = TraitBoolMethodPropertiesCommand.getConstraintsMap()

        then: 'only 4 defined public properties are there'
        constrainedProperties.size() == 3
        constrainedProperties.containsKey('anotherPublicProperty')
        constrainedProperties.containsKey('booleanPublicProperty')
        constrainedProperties.containsKey('getterOnly')
    }
}

/**
 * Command with properties only
 */
class SimplePropertiesCommand implements Validateable {
    List list
    Map map
    String string
    Book book

    private String fisrtName
    protected String secondName
    static String finalName
    private static String foo
    protected static String bar
}

/**
 * Command with properties from super class only
 */
class InheritedPropertiesCommand extends SimplePropertiesCommand implements Validateable {}

/**
 * Command with getter/setter methods
 */
class MethodPropertiesCommand implements Validateable {

    /**
     * booleanPublicProperty should  be constrained because it has a getter and setter
     */
    Boolean getBooleanPublicProperty() {}

    void setBooleanPublicProperty(Boolean value) {}

    /**
     * publicProperty should not be constrained because it should be called getPublicProperty when the type is Boolean
     */
    Boolean isPublicProperty() {}

    void setPublicProperty(Boolean value) {}

    /**
     * anotherPublicProperty should be constrained because those getter and setter
     */
    boolean isAnotherPublicProperty() {}

    void setAnotherPublicProperty(boolean value) {}

    protected String getProtectedProperty() {}

    protected void setProtectedProperty(String value) {}

    private String getPrivateProperty() {}

    private void setPrivateProperty(String value) {}

    static String getStaticPublicProperty() {}

    static void setStaticPublicProperty(String value) {}

    static protected String getStaticProtectedProperty() {}

    static protected void setStaticProtectedProperty(String value) {}

    static private String getStaticPrivateProperty() {}

    static private void setStaticPrivateProperty(String value) {}

    String getGetterOnly() {}

    protected String getProtectedGetterOnly() {}

    private String getPrivateGetterOnly() {}

    static String getStaticGetterOnly() {}

    static protected String getStaticProtectedGetterOnly() {}

    static private String getStaticPrivateGetterOnly() {}

    void setSetterOnly(String value) {}

    protected void setProtectedSetterOnly(String value) {}

    private void setPrivateSetterOnly(String value) {}

    static void setStaticSetterOnly(String value) {}

    static protected void setStaticProtectedSetterOnly(String value) {}

    static private void setStaticPrivateSetterOnly(String value) {}
}

/**
 * Command with method properties from super class
 */
class InheritedMethodPropertiesCommand extends MethodPropertiesCommand implements Validateable {}

/**
 * Trait with properties only
 */
trait SimplePropertiesTrait implements Validateable {
    List list
    Map map
    String string
    Book book

    private String fisrtName
    static String finalName
    private static String foo
}

/**
 * Trait with getter/setter methods
 */
trait MethodPropertiesTrait {

    /**
     * booleanPublicProperty should  be constrained because it has a getter and setter
     */
    Boolean getBooleanPublicProperty() {}

    void setBooleanPublicProperty(Boolean value) {}

    /**
     * publicProperty should not be constrained because it should be called getPublicProperty when the type is Boolean
     */
    Boolean isPublicProperty() {}

    void setPublicProperty(Boolean value) {}

    /**
     * anotherPublicProperty should be constrained because those getter and setter
     */
    boolean isAnotherPublicProperty() {}

    void setAnotherPublicProperty(boolean value) {}

    private String getPrivateProperty() {}

    private void setPrivateProperty(String value) {}

    static String getStaticPublicProperty() {}

    static void setStaticPublicProperty(String value) {}

    static private String getStaticPrivateProperty() {}

    static private void setStaticPrivateProperty(String value) {}

    String getGetterOnly() {}

    private String getPrivateGetterOnly() {}

    static String getStaticGetterOnly() {}

    static private String getStaticPrivateGetterOnly() {}

    void setSetterOnly(String value) {}

    private void setPrivateSetterOnly(String value) {}

    static void setStaticSetterOnly(String value) {}

    static private void setStaticPrivateSetterOnly(String value) {}
}

/**
 * Domain with properties from trait
 */
class TraitPropertiesCommand implements SimplePropertiesTrait, Validateable {}

/**
 * Command with method properties from trait
 */
class TraitMethodPropertiesCommand implements MethodPropertiesTrait, Validateable {}

/**
 * Command with bool methods - `is` instead of `get`
 */
class BoolMethodPropertiesCommand implements Validateable {

    /**
     * booleanPublicProperty should  be constrained because it has a getter and setter
     */
    Boolean getBooleanPublicProperty() {}

    void setBooleanPublicProperty(Boolean value) {}

    /**
     * publicProperty should not be constrained because it should be called getPublicProperty when the type is Boolean
     */
    Boolean isPublicProperty() {}

    void setPublicProperty(Boolean value) {}

    /**
     * anotherPublicProperty should be constrained because those getter and setter
     */
    boolean isAnotherPublicProperty() {}

    void setAnotherPublicProperty(boolean value) {}

    protected Boolean getProtectedProperty() {}

    protected void setProtectedProperty(Boolean value) {}

    private Boolean getPrivateProperty() {}

    private void setPrivateProperty(Boolean value) {}

    static Boolean getStaticPublicProperty() {}

    static void setStaticPublicProperty(Boolean value) {}

    static protected Boolean getStaticProtectedProperty() {}

    static protected void setStaticProtectedProperty(Boolean value) {}

    static private Boolean getStaticPrivateProperty() {}

    static private void setStaticPrivateProperty(Boolean value) {}

    Boolean getGetterOnly() {}

    protected Boolean getProtectedGetterOnly() {}

    private Boolean getPrivateGetterOnly() {}

    static Boolean getStaticGetterOnly() {}

    static protected Boolean getStaticProtectedGetterOnly() {}

    static private Boolean getStaticPrivateGetterOnly() {}

    void setSetterOnly(Boolean value) {}

    protected void setProtectedSetterOnly(Boolean value) {}

    private void setPrivateSetterOnly(Boolean value) {}

    static void setStaticSetterOnly(Boolean value) {}

    static protected void setStaticProtectedSetterOnly(Boolean value) {}

    static private void setStaticPrivateSetterOnly(Boolean value) {}
}

/**
 * Trait with getter/setter methods
 */
trait BoolMethodPropertiesTrait {

    /**
     * booleanPublicProperty should  be constrained because it has a getter and setter
     */
    Boolean getBooleanPublicProperty() {}

    void setBooleanPublicProperty(Boolean value) {}

    /**
     * publicProperty should not be constrained because it should be called getPublicProperty when the type is Boolean
     */
    Boolean isPublicProperty() {}

    void setPublicProperty(Boolean value) {}

    /**
     * anotherPublicProperty should be constrained because those getter and setter
     */
    boolean isAnotherPublicProperty() {}

    void setAnotherPublicProperty(boolean value) {}

    private Boolean getPrivateProperty() {}

    private void setPrivateProperty(Boolean value) {}

    static Boolean getStaticPublicProperty() {}

    static void setStaticPublicProperty(Boolean value) {}

    static private Boolean getStaticPrivateProperty() {}

    static private void setStaticPrivateProperty(Boolean value) {}

    Boolean getGetterOnly() {}

    private Boolean getPrivateGetterOnly() {}

    static Boolean getStaticGetterOnly() {}

    static private Boolean getStaticPrivateGetterOnly() {}

    void setSetterOnly(Boolean value) {}

    private void setPrivateSetterOnly(Boolean value) {}

    static void setStaticSetterOnly(Boolean value) {}

    static private void setStaticPrivateSetterOnly(Boolean value) {}
}

/**
 * Command with inherited bool method properties from super class
 */
class InheritedBoolMethodPropertiesCommand extends BoolMethodPropertiesCommand implements Validateable {}

/**
 * Command with inherited bool method properties from trait
 */
class TraitBoolMethodPropertiesCommand implements BoolMethodPropertiesTrait, Validateable {}

/**
 * Helper class
 */
class Book {}


