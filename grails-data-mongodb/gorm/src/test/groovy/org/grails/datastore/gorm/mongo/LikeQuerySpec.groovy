package org.grails.datastore.gorm.mongo

import grails.gorm.tests.Pet
import grails.gorm.tests.GormDatastoreSpec

class LikeQuerySpec extends GormDatastoreSpec {

    void "Test for like query"() {
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
        def results = Pet.findAllByNameLike(search)

        then:
        results*.name == expected

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
}