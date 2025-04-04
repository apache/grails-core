/*
 * Copyright 2017 original authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package grails.gorm.tests.validation

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.SessionFactory
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Issue('https://github.com/grails/grails-data-mapping/issues/1004')
class UniqueWithHasOneSpec extends Specification {

    @AutoCleanup @Shared HibernateDatastore hibernateDatastore = new HibernateDatastore(getClass().getPackage())
    @Shared SessionFactory sessionFactory = hibernateDatastore.sessionFactory

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

    static hasOne = [bar: Bar]

    String name

    static constraints = {
        bar nullable: true
        name unique: "bar"
    }

}

@Entity
class Bar {

    static belongsTo = [foo: Foo]

    String name

    static constraints = {
        foo nullable: true
    }

}
