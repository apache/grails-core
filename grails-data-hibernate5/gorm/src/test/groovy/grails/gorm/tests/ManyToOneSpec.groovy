package grails.gorm.tests

import grails.gorm.annotation.Entity
import org.grails.orm.hibernate.GormSpec

/**
 * Created by graemerocher on 27/06/16.
 */
class ManyToOneSpec extends GormSpec {

    static {
        System.setProperty("org.jboss.logging.provider", "slf4j")
    }

    void "Test many-to-one association"() {
        when:"A many-to-one association is saved"
        Foo foo1 = new Foo(fooDesc: "Foo One").save()
        Foo foo2 = new Foo(fooDesc: "Foo Two").save()
        Foo foo3 = new Foo(fooDesc: "Foo Three").save()


        foo3.bar = new Bar(barDesc: "Bar Three",foo:foo3)
        foo3.save(flush:true)
        foo1.bar = new Bar(barDesc: "Bar One",foo:foo1)
        foo1.save(flush:true)
        foo2.bar = new Bar(barDesc: "Bar Two", foo:foo2)
        foo2.save(flush:true)

        session.clear()
        println "RETRIEVING FOOS!"
        def foos = Foo.findAll()
        println("Foos:")
        foos.each{ f ->
            println(f.fooDesc + " -> " + f.bar.barDesc)
        }

        session.clear()

        println "RETRIEVING BARS!"
        def bars = Bar.findAll()
        println("Bars:")
        bars.each{ b ->
            println(b.barDesc + " -> " + b.foo.fooDesc)
        }
        session.clear()

        foo1 = Foo.get(foo1.id)
        foo2 = Foo.get(foo2.id)
        foo3 = Foo.get(foo3.id)



        Bar bar1 = Bar.findByBarDesc("Bar One")
        Bar bar2 = Bar.findByBarDesc("Bar Two")
        Bar bar3 = Bar.findByBarDesc("Bar Three")

        then:"The data model is correct"
        foo1.fooDesc == "Foo One"
        foo1.bar.barDesc == "Bar One"
        foo2.fooDesc == "Foo Two"
        foo2.bar.barDesc == "Bar Two"
        foo3.fooDesc == "Foo Three"
        foo3.bar.barDesc == "Bar Three"
        bar1.barDesc == "Bar One"
        bar1.foo.fooDesc == "Foo One"
        bar2.barDesc == "Bar Two"
        bar2.foo.fooDesc == "Foo Two"
        bar3.barDesc == "Bar Three"
        bar3.foo.fooDesc == "Foo Three"
    }
    @Override
    List getDomainClasses() {
        [Foo,Bar]
    }
}

@Entity
class Foo {

    String fooDesc

    Bar bar

    static mapping = {
        id generator:'identity'
    }

    static constraints = {
        bar(nullable: true)
    }
}

@Entity
class Bar {

    String barDesc

    static belongsTo = [ foo: Foo ]

    static mapping = {
        id generator:'identity'
    }

    static constraints = {
    }
}