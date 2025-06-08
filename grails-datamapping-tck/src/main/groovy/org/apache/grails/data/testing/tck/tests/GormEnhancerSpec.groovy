/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.grails.data.testing.tck.tests

import org.apache.grails.data.testing.tck.domains.ChildEntity
import org.apache.grails.data.testing.tck.domains.TestEntity
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

/**
 * @author graemerocher
 */
class GormEnhancerSpec extends GrailsDataTckSpec {

    void setupSpec() {
        manager.domainClasses.addAll([TestEntity, ChildEntity])
    }

    void "Test basic CRUD operations"() {
        given:
        def t

        when:
        t = TestEntity.get(1)

        then:
        t == null

        when:
        t = new TestEntity(name: "Bob", child: new ChildEntity(name: "Child"))
        t.save()

        then:
        t.id != null

        when:
        def results = TestEntity.list()

        then:
        1 == results.size()
        "Bob" == results[0].name

        when:
        t = TestEntity.get(t.id)

        then:
        t != null
        "Bob" == t.name
    }

    void "Test simple dynamic finder"() {

        given:
        def t = new TestEntity(name: "Bob", child: new ChildEntity(name: "Child"))
        t.save()

        t = new TestEntity(name: "Fred", child: new ChildEntity(name: "Child"))
        t.save()

        when:
        def results = TestEntity.list()
        def bob = TestEntity.findByName("Bob")

        then:
        2 == results.size()
        bob != null
        "Bob" == bob.name
    }

    void "Test dynamic finder with disjunction"() {
        given:
        def age = 40
        ["Bob", "Fred", "Barney"].each {
            new TestEntity(name: it, age: age++, child: new ChildEntity(name: "$it Child")).save()
        }

        when:
        def results = TestEntity.findAllByNameOrAge("Barney", 40)
        def barney = results.find { it.name == "Barney" }
        def bob = results.find {
            it.age == 40
        }

        then:
        3 == TestEntity.count()
        2 == results.size()
        barney != null
        42 == barney.age
        bob != null
        "Bob" == bob.name
    }

    void "Test getAll() method"() {
        given:
        def age = 40
        def ids = []
        ["Bob", "Fred", "Barney"].each {
            ids.add(new TestEntity(name: it, age: age++, child: new ChildEntity(name: "$it Child")).save().id)
        }

        when:
        def results = TestEntity.getAll(ids[0], ids[1])

        then:
        2 == results.size()
    }

    void "Test ident() method"() {
        given:
        def t

        when:
        t = new TestEntity(name: "Bob", child: new ChildEntity(name: "Child"))
        t.save()

        then:
        t.id != null
        t.id == t.ident()
    }

    void "Test dynamic finder with pagination parameters"() {
        given:
        def age = 40
        ["Bob", "Fred", "Barney", "Frank"].each {
            new TestEntity(name: it, age: age++, child: new ChildEntity(name: "$it Child")).save()
        }

        when:
        def total = TestEntity.count()

        then:
        4 == total

        2 == TestEntity.findAllByNameOrAge("Barney", 40).size()
        1 == TestEntity.findAllByNameOrAge("Barney", 40, [max: 1]).size()
    }

    void "Test in list query"() {
        given:
        def age = 40
        ["Bob", "Fred", "Barney", "Frank"].each {
            new TestEntity(name: it, age: age++, child: new ChildEntity(name: "$it Child")).save()
        }

        when:
        def total = TestEntity.count()

        then:
        4 == total
        2 == TestEntity.findAllByNameInList(["Fred", "Frank"]).size()
        1 == TestEntity.findAllByNameInList(["Joe", "Frank"]).size()
        0 == TestEntity.findAllByNameInList(["Jeff", "Jack"]).size()
        2 == TestEntity.findAllByNameInListOrName(["Joe", "Frank"], "Bob").size()
    }

    void "Test like query"() {
        given:
        def age = 40
        ["Bob", "Fred", "Barney", "Frank", "frita"].each {
            new TestEntity(name: it, age: age++, child: new ChildEntity(name: "$it Child")).save()
        }

        when:
        def results = TestEntity.findAllByNameLike("Fr%")

        then:
        2 == results.size()
        results.find { it.name == "Fred" } != null
        results.find { it.name == "Frank" } != null
    }

    void "Test ilike query"() {
        given:
        def age = 40
        ["Bob", "Fred", "Barney", "Frank", "frita"].each {
            new TestEntity(name: it, age: age++, child: new ChildEntity(name: "$it Child")).save()
        }

        when:
        def results = TestEntity.findAllByNameIlike("fr%")

        then:
        3 == results.size()
        results.find { it.name == "Fred" } != null
        results.find { it.name == "Frank" } != null
        results.find { it.name == "frita" } != null
    }

    void "Test count by query"() {

        given:
        def age = 40
        ["Bob", "Fred", "Barney"].each {
            new TestEntity(name: it, age: age++, child: new ChildEntity(name: "$it Child")).save()
        }

        when:
        def total = TestEntity.count()

        then:
        3 == total
        3 == TestEntity.list().size()
        2 == TestEntity.countByNameOrAge("Barney", 40)
        1 == TestEntity.countByNameAndAge("Bob", 40)
    }

    void "Test dynamic finder with conjunction"() {
        given:
        def age = 40
        ["Bob", "Fred", "Barney"].each {
            new TestEntity(name: it, age: age++, child: new ChildEntity(name: "$it Child")).save()
        }

        when:
        def total = TestEntity.count()

        then:
        3 == total
        3 == TestEntity.list().size()

        TestEntity.findByNameAndAge("Bob", 40)
        !TestEntity.findByNameAndAge("Bob", 41)
    }

    void "Test count() method"() {
        given:
        def t

        when:
        t = new TestEntity(name: "Bob", child: new ChildEntity(name: "Child"))
        t.save()

        then:
        1 == TestEntity.count()

        when:
        t = new TestEntity(name: "Fred", child: new ChildEntity(name: "Child"))
        t.save()

        then:
        2 == TestEntity.count()
    }
}
