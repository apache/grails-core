package grails.gorm.tests

/*
 * Copyright 2014 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * @author graemerocher
 */
class LikeQuerySpec extends GormDatastoreSpec {
    @Override
    List getDomainClasses() {
        [Pet]
    }

    void "Test for like query"() {
        given:
        new Pet(name: "foo").save(flush:true, failOnError:true)
        new Pet(name: "bar").save(flush:true, failOnError:true)
        new Pet(name: "baz").save(flush:true, failOnError:true)
        new Pet(name: "foobar").save(flush:true, failOnError:true)
        new Pet(name: "*").save(flush:true, failOnError:true)
        new Pet(name: "**").save(flush:true, failOnError:true)
        new Pet(name: "***").save(flush:true, failOnError:true)

        session.transaction.commit()
        session.clear()

        when:

        def results = Pet.findAllByNameLike(search)
        println "FOUND ${results*.name} (EXPECTED $expected) for search [$search]"
        then:
        results*.name.sort() == expected

        where:
        search  | expected
        'f'     | []
        'foo'   | ['foo']
        'f%'    | ['foo', 'foobar']
        'f%o'   | ['foo']
        '%foo'  | ['foo']
        'foo%'  | ['foo', 'foobar']
        '%foo%' | ['foo', 'foobar']
        'f.*'   | []
        '*'     | ['*']
        '**'    | ['**']
        '.*'    | []
    }

    void "Test for ilike query"() {
        given:
        new Pet(name: "Foo").save(flush:true, failOnError:true)
        new Pet(name: "Bar").save(flush:true, failOnError:true)
        new Pet(name: "Baz").save(flush:true, failOnError:true)
        new Pet(name: "Foobar").save(flush:true, failOnError:true)
        new Pet(name: "*").save(flush:true, failOnError:true)
        new Pet(name: "**").save(flush:true, failOnError:true)
        new Pet(name: "***").save(flush:true, failOnError:true)
        session.transaction.commit()
        session.clear()

        when:
        def results = Pet.findAllByNameIlike(search)

        then:
        results*.name.sort() == expected

        where:
        search  | expected
        'f'     | []
        'foo'   | ['Foo']
        'f%'    | ['Foo', 'Foobar']
        'f%o'   | ['Foo']
        '%foo'  | ['Foo']
        'foo%'  | ['Foo', 'Foobar']
        '%foo%' | ['Foo', 'Foobar']
        'f.*'   | []
        '*'     | ['*']
        '**'    | ['**']
        '.*'    | []
    }


    void "Test for rlike query"() {
        given:
        new Pet(name: "foo").save(flush:true, failOnError:true)
        new Pet(name: "bar").save(flush:true, failOnError:true)
        new Pet(name: "baz").save(flush:true, failOnError:true)
        new Pet(name: "foobar").save(flush:true, failOnError:true)
        new Pet(name: "*").save(flush:true, failOnError:true)
        new Pet(name: "**").save(flush:true, failOnError:true)
        new Pet(name: "***").save(flush:true, failOnError:true)
        session.clear()

        when:
        def results = Pet.findAllByNameRlike(search)

        then:
        results*.name.sort() == expected

        where:
        search  | expected
        'f'     | []
        'foo'   | ['foo']
        /f\w+/    | ['foo', 'foobar']
        'f.o'   | ['foo']
        '.*foo'  | ['foo']
        'foo.*'  | ['foo', 'foobar']
    }
}
