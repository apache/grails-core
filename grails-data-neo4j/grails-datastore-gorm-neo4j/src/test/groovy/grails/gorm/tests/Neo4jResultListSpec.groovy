package grails.gorm.tests

class Neo4jResultListSpec extends GormDatastoreSpec {

    @Override
    List getDomainClasses() {
        [Pet]
    }


    void "Test mutate result list"() {
        given:
        new Pet(name: "foo", age: 10).save(flush:true, failOnError:true)
        new Pet(name: "bar", age: null).save(flush:true, failOnError:true)
        new Pet(name: "", age: 12).save(flush:true, failOnError:true)
        session.clear()

        when:
        def list = Pet.list()
        list.add(new Pet(name: "another", age: 10))

        then:
        list.size() == 4
    }
}
