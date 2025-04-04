import grails.gorm.annotation.Entity
import grails.gorm.tests.GormDatastoreSpec

class DirtyCheckingSpec extends GormDatastoreSpec {
    
    void "When marking whole class dirty, then derived and transient properties are still not dirty"() {
        when:
        TestBook book = new TestBook()
        book.title = "Test"
        and: "mark class as not dirty - to clear previous dirty tracking"
        book.trackChanges()

        then:
        !book.hasChanged()

        when: "Mark whole class as dirty"
        book.markDirty()

        then: "whole class is dirty"
        book.hasChanged()

        and: "The formula and transient properties are not dirty"
        !book.hasChanged('formulaProperty')
        !book.hasChanged('transientProperty')
        
        and: "Other properties are"
        book.hasChanged('id')
        book.hasChanged('title')

    }

    void "Test that dirty tracking doesn't apply on Entity's transient properties"() {
        when:
        TestBook book = new TestBook()
        book.title = "Test"
        and: "mark class as not dirty, clear previous dirty tracking"
        book.trackChanges()

        then:
        !book.hasChanged()

        when: "update transient property"
        book.transientProperty = "new transient value"

        then: "class is not dirty"
        !book.hasChanged()

        and: "transient properties are not dirty"
        !book.hasChanged('transientProperty')
    }

    @Override
    List getDomainClasses() {
        [TestBook]
    }
}

@Entity
class TestBook implements Serializable {

    Long id
    String title

    String formulaProperty

    String transientProperty
    
    static mapping = {
        formulaProperty(formula: 'name || \' (formula)\'')
    }
    
    static transients = ['transientProperty']
}