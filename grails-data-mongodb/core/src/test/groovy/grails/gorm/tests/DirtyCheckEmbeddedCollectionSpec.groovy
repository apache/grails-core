package grails.gorm.tests

import grails.persistence.Entity

import org.bson.types.ObjectId

class DirtyCheckEmbeddedCollectionSpec extends GormDatastoreSpec {

    def "Test that changes to basic collections are detected"() {
        given:"A valid bar instance"
            def bar = createBar()
            session.clear()
        when:"A basic collection is modified"
            bar = Bar.get(bar.id)
            bar.strings.add("hello")
            bar.save(flush:true)
            session.clear()
            bar = Bar.get(bar.id)
        then:"The changes are reflected correctly in the persisted instance"
            bar.strings.size() == 3

         when:"A basic collection is cleared"
            bar.strings.clear()
            bar.save(flush:true)
            session.clear()
            bar = Bar.get(bar.id)

        then:"The collection is empty"
            bar.strings.size() == 0
    }

    def "Test that an embedded collection can be cleared"() {
        given:"valid foo and bar instances"
            def foo = createFooBar()
            session.clear()

        when:"foo is looked up"
            foo == Foo.get(foo.id)

        then:"It has 1 bar"
            foo.bars.size() == 1

        when:"The collection is cleared"
            foo.bars.clear()
            foo.save(flush:true)
            session.clear()

            foo == Foo.get(foo.id)
        then:"The collection is empty on nexted lookup"
            foo.bars.size() == 0
    }

    protected createBar() {
        Bar bar = new Bar(foo: 'foo')
        bar.strings.add("test")
        bar.save(flush: true)
        //bar is correctly saved
        bar = Bar.get(bar.id)
        bar.strings.add("test2")
        bar.save(flush:true)
    }

    protected createFooBar() {
        def bar = new Bar(foo:"test")
        def foo = new Foo(testProperty:"test")
        foo.bars.add(bar)
        foo.save(flush:true)
    }

    @Override
    List getDomainClasses() {
        [Foo, Bar]
    }
}

@Entity
class Foo {
    ObjectId id
    String testProperty
    Set bars = []
    static hasMany = [bars:Bar]
    static embedded = ['bars']
}

@Entity
class Bar {
    ObjectId id
    String foo
    List<String> strings = []
}
