/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package grails.gorm.tests

import grails.persistence.Entity
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.bson.types.ObjectId

class DirtyCheckEmbeddedCollectionSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([Foo, Bar])
    }

    def "Test that changes to basic collections are detected"() {
        given: "A valid bar instance"
        def bar = createBar()
        manager.session.clear()

        when: "A basic collection is modified"
        bar = Bar.get(bar.id)
        bar.strings.add("hello")
        bar.save(flush: true)
        manager.session.clear()
        bar = Bar.get(bar.id)

        then: "The changes are reflected correctly in the persisted instance"
        bar.strings.size() == 3

        when: "A basic collection is cleared"
        bar.strings.clear()
        bar.save(flush: true)
        manager.session.clear()
        bar = Bar.get(bar.id)

        then: "The collection is empty"
        bar.strings.size() == 0
    }

    def "Test that an embedded collection can be cleared"() {
        given: "valid foo and bar instances"
        def foo = createFooBar()
        manager.session.clear()

        when: "foo is looked up"
        foo == Foo.get(foo.id)

        then: "It has 1 bar"
        foo.bars.size() == 1

        when: "The collection is cleared"
        foo.bars.clear()
        foo.save(flush: true)
        manager.session.clear()

        foo == Foo.get(foo.id)
        then: "The collection is empty on nexted lookup"
        foo.bars.size() == 0
    }

    protected createBar() {
        Bar bar = new Bar(foo: 'foo')
        bar.strings.add("test")
        bar.save(flush: true)
        //bar is correctly saved
        bar = Bar.get(bar.id)
        bar.strings.add("test2")
        bar.save(flush: true)
    }

    protected createFooBar() {
        def bar = new Bar(foo: "test")
        def foo = new Foo(testProperty: "test")
        foo.bars.add(bar)
        foo.save(flush: true)
    }
}

@Entity
class Foo {
    ObjectId id
    String testProperty
    Set bars = []
    static hasMany = [bars: Bar]
    static embedded = ['bars']
}

@Entity
class Bar {
    ObjectId id
    String foo
    List<String> strings = []
}
