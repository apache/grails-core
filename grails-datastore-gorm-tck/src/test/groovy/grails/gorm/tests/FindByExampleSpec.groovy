package grails.gorm.tests

class FindByExampleSpec extends GormDatastoreSpec {

    def "Test findAll by example"() {
        given:
            new Plant(name:"Pineapple", goesInPatch:false).save()
            new Plant(name:"Cabbage", goesInPatch:true).save()
            new Plant(name:"Kiwi", goesInPatch:false).save(flush:true)
            session.clear()
        when:
            def results = Plant.findAll(new Plant(goesInPatch:false))
        then:
            results.size() == 2
            "Pineapple" in results*.name
            "Kiwi" in results*.name

        when:
            results = Plant.findAll(new Plant(name:"Cabbage",goesInPatch:false))

        then:
            results.size() == 0

        when:
            results = Plant.findAll(new Plant(name:"Cabbage",goesInPatch:true))

        then:
            results.size() == 1
            "Cabbage" in results*.name
    }

    def "Test find by example"() {
        given:
            new Plant(name:"Pineapple", goesInPatch:false).save()
            new Plant(name:"Cabbage", goesInPatch:true).save()
            new Plant(name:"Kiwi", goesInPatch:false).save(flush:true)
            session.clear()

        when:
            Plant result = Plant.find(new Plant(name:"Cabbage",goesInPatch:false))

        then:
            result == null

        when:
            result = Plant.find(new Plant(name:"Cabbage",goesInPatch:true))

        then:
            result != null
            result.name == "Cabbage"
    }
}
