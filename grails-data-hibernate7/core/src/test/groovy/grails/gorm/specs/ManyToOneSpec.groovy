/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package grails.gorm.specs

import grails.gorm.annotation.Entity
import org.apache.grails.data.hibernate7.core.GrailsDataHibernate7TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

/**
 * Created by graemerocher on 27/06/16.
 */
class ManyToOneSpec extends GrailsDataTckSpec<GrailsDataHibernate7TckManager> {

    void setupSpec() {
        manager.addAllDomainClasses([Foo, Bar])
    }

    static {
        System.setProperty('org.jboss.logging.provider', 'slf4j')
    }

    void 'Test many-to-one association'() {
        when: 'A many-to-one association is saved'
        Foo foo1 = new Foo(fooDesc: 'Foo One').save(flush: true)
        Foo foo2 = new Foo(fooDesc: 'Foo Two').save(flush: true)
        Foo foo3 = new Foo(fooDesc: 'Foo Three').save(flush: true)

        manager.session.clear() // Clear session to ensure fresh entities

        // Retrieve fresh Foo instances if needed, or work with detached instances
        Foo loadedFoo1 = Foo.get(foo1.id)
        Foo loadedFoo2 = Foo.get(foo2.id)
        Foo loadedFoo3 = Foo.get(foo3.id)

        // Create and save Bar instances
        new Bar(barDesc: 'Bar One', foo: loadedFoo1).save(flush: true)
        new Bar(barDesc: 'Bar Two', foo: loadedFoo2).save(flush: true)
        new Bar(barDesc: 'Bar Three', foo: loadedFoo3).save(flush: true)

        manager.session.clear()
        println 'RETRIEVING FOOS!'
        def foos = Foo.findAll()
        println('Foos:')
        foos.each { f ->
            println(f.fooDesc + ' -> ' + f.bar.barDesc)
        }

        manager.session.clear()

        println 'RETRIEVING BARS!'
        def bars = Bar.findAll()
        println('Bars:')
        bars.each { b ->
            println(b.barDesc + ' -> ' + b.foo.fooDesc)
        }
        manager.session.clear()

        foo1 = Foo.get(foo1.id)
        foo2 = Foo.get(foo2.id)
        foo3 = Foo.get(foo3.id)

        Bar bar1 = Bar.findByBarDesc('Bar One')
        Bar bar2 = Bar.findByBarDesc('Bar Two')
        Bar bar3 = Bar.findByBarDesc('Bar Three')

        then: 'The data model is correct'
        foo1.fooDesc == 'Foo One'
        foo1.bar.barDesc == 'Bar One'
        foo2.fooDesc == 'Foo Two'
        foo2.bar.barDesc == 'Bar Two'
        foo3.fooDesc == 'Foo Three'
        foo3.bar.barDesc == 'Bar Three'
        bar1.barDesc == 'Bar One'
        bar1.foo.fooDesc == 'Foo One'
        bar2.barDesc == 'Bar Two'
        bar2.foo.fooDesc == 'Foo Two'
        bar3.barDesc == 'Bar Three'
        bar3.foo.fooDesc == 'Foo Three'
    }
}

@Entity
class Foo {

    String fooDesc

    Bar bar

    static hasOne = [bar: Bar]

    static mapping = {
        id generator: 'identity'
    }

    static constraints = {
        bar(nullable: true)
    }
}

@Entity
class Bar {

    String barDesc

    static belongsTo = [foo: Foo]

    static mapping = {
        id generator: 'identity'
    }

    static constraints = {
    }
}