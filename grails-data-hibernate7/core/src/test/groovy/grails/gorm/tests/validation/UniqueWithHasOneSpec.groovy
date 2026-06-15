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
package grails.gorm.tests.validation

import grails.gorm.annotation.Entity
import grails.gorm.tests.HibernateGormDatastoreSpec
import grails.gorm.transactions.Rollback
import spock.lang.Issue

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Issue('https://github.com/grails/grails-data-mapping/issues/1004')
class UniqueWithHasOneSpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.addAllDomainClasses([Foo, Bar])
    }

    @Rollback
    void "test unique constraint with hasOne"() {
        when:
        Foo foo = new Foo(name: "foo")
        Bar bar = new Bar(name: "bar")
        foo.bar = bar
        bar.foo = foo
        foo.save failOnError: true

        then:
        Foo.count == 1
        Bar.count == 1
    }
}

@Entity
class Foo {

    String name
    Bar bar

    static hasOne = [bar: Bar]


    static constraints = {
        bar nullable: true, unique: true
    }

    static mapping = {
        bar column: 'bar_id'
    }
}

@Entity
class Bar {

    String name
    static belongsTo = [ foo: Foo]

    static constraints = {
    }
}
