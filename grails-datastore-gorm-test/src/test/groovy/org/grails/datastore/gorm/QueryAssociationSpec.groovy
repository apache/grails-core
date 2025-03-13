package org.grails.datastore.gorm

import grails.gorm.tck.ChildEntity
import grails.gorm.tests.GormDatastoreSpec
import grails.gorm.tck.PlantCategory
import grails.gorm.tck.TestEntity

class QueryAssociationSpec extends GormDatastoreSpec {

    void "Test query one-to-one association with disjunction"() {
        given:
            new TestEntity(name:"Bob", age: 44, child:new ChildEntity(name:"Nick")).save(flush:true)

        when: "A query on the child association wrapped in a disjunction"
            def results = TestEntity.withCriteria {
                or {
                    child {
                        eq 'name', 'Nick'
                    }
                    eq 'name', 'James'
                }
            }
        then: "Check that the entity named Bob is returned"
            TestEntity.count() == 1
            results.size() == 1
            results[0].name == "Bob"
    }

    void "Test query one-to-one association with conjunction"() {
        given:
            new TestEntity(name:"Bob", age: 44, child:new ChildEntity(name:"Nick")).save(flush:true)

        when: "A query on the child association wrapped in a conjunction"
            def results = TestEntity.withCriteria {
                and {
                    child {
                        eq 'name', 'Bob'
                    }
                    eq 'name', 'Bob'
                }
            }
        then: "Check that no entities are returned"
            TestEntity.count() == 1
            results.size() == 0
    }

    void "Test query one-to-one association"() {
        given:
            new TestEntity(name:"Bob", age: 44, child:new ChildEntity(name:"Nick")).save(flush:true)
            new TestEntity(name:"Fred", age: 32, child:new ChildEntity(name:"Jeff")).save(flush:true)
            new TestEntity(name:"Charlie", age: 38, child:new ChildEntity(name:"Rosie")).save(flush:true)
            new TestEntity(name:"Joe", age: 38, child:new ChildEntity(name:"Jake")).save(flush:true)

        when: "A query on the child association with an equals criterion is executed"
            def results = TestEntity.withCriteria {
                child {
                    eq 'name', 'Jeff'
                }
            }
        then: "Check that the entity named Fred is returned"
            TestEntity.count() == 4
            results.size() == 1
            results[0].name == "Fred"

        when: "A like criterion is executed on the child association"
            results = TestEntity.withCriteria {
                child {
                    like 'name', 'J%'
                }
                order  "name"
            }

        then: "Check that we get 2 results back"
            results.size() == 2
            results[0].name == "Fred"
            results[1].name == "Joe"

        when: "An ilike criterion is executed on the child association"
            results = TestEntity.withCriteria {
                child {
                    ilike 'name', 'j%'
                }
                order  "name"
            }

        then: "Check that we get 2 results back"
            results.size() == 2
            results[0].name == "Fred"
            results[1].name == "Joe"

        when: "An ilike criterion is executed on the entity"
            results = TestEntity.withCriteria {
                ilike 'name', '%e%'
                order  "name"
            }

        then: "Check that we get 2 results back"
            results.size() == 3
            results[0].name == "Charlie"
            results[1].name == "Fred"
            results[2].name == "Joe"

        when: "A not equals criterion is executed in a child association"
            results = TestEntity.withCriteria {
                child {
                    ne 'name', 'Rosie'
                }
                order  "name"
            }

        then: "Check that we get 3 results back"
            results.size() == 3
            results[0].name == "Bob"
            results[1].name == "Fred"
            results[2].name == "Joe"

        when: "A between query is used"
            results = TestEntity.withCriteria {
                child {
                    between 'id', 1L, 2L
                }
                order  "name"
            }

        then: "Check we get the correct results"
            results.size() == 2
            results[0].name == "Bob"
            results[1].name == "Fred"

        when: "A greater than query is used"
            results = TestEntity.withCriteria {
                child {
                    gt 'id', 2L
                }
                order  "name"
            }

        then: "Check we get the correct results"
            results.size() == 2
            results[0].name == "Charlie"
            results[1].name == "Joe"

        when: "A less than query is used"
            results = TestEntity.withCriteria {
                child {
                    lt 'id', 3L
                }
                order  "name"
            }

        then: "Check we get the correct results"
            results.size() == 2
            results[0].name == "Bob"
            results[1].name == "Fred"

        when: "An in query is used"
            results = TestEntity.withCriteria {
                child {
                    inList 'name', ["Nick", "Rosie"]
                }
                order  "name"
            }

        then: "We get Bob and Charlie back"

            results.size() == 2
            results[0].name == "Bob"
            results[1].name == "Charlie"
            
        when: "An in query is used with immutable list containing GStrings"
            results = TestEntity.withCriteria {
                child {
                    inList 'name', ["Nick", "${'Rosie'}"].asImmutable()
                }
                order  "name"
            }

        then: "We get Bob and Charlie back"

            results.size() == 2
            results[0].name == "Bob"
            results[1].name == "Charlie"

        when: "Multiple child criterion are used"
            results = TestEntity.withCriteria {
                child {
                    inList 'name', ["Nick", "Rosie"]
                    gt 'id', 2L
                }
                order  "name"
            }

        then: "We get the expected results back"
            results.size() == 1
            results[0].name == "Charlie"

        when: "A disjunction is used"
            results = TestEntity.withCriteria {
                child {
                    or {
                        inList 'name', ["Nick", "Rosie"]
                        gt 'id', 2L
                    }
                }
                order  "name"
            }

        then: "We get the expected results back"

            results.size() == 3
            results[0].name == "Bob"
            results[1].name == "Charlie"
            results[2].name == "Joe"

        when: "A conjuntion is used"
            results = TestEntity.withCriteria {
                child {
                    and {
                        inList 'name', ["Nick", "Rosie"]
                        gt 'id', 2L
                    }
                }
                order  "name"
            }

        then: "We get the expected results back"
            results.size() == 1
            results[0].name == "Charlie"
    }

    void "Test query one-to-many association"() {
        given:
            new PlantCategory(name:"Tropical")
                    .addToPlants(name:"Pineapple")
                    .addToPlants(name:"Mango")
                    .addToPlants(name:"Lychee")
                    .save()
            new PlantCategory(name:"Veg")
                    .addToPlants(name:"Cabbage", goesInPatch:true)
                    .addToPlants(name:"Carrot", goesInPatch:true)
                    .addToPlants(name:"Pumpkin", goesInPatch:true)
                    .addToPlants(name:"Tomatoe")
                    .save(flush:true)
            new PlantCategory(name:"Nuts")
                .addToPlants(name:"Walnut")
                .addToPlants(name:"Coconut")
                .save(flush:true)

        when: "The session is cleared"
            session.clear()
            def categories = PlantCategory.list(sort:"name")

        then: "Check that the state of the data is correct"

            categories.size() == 3
            categories[0] instanceof PlantCategory
            categories[0].plants?.size() == 2

        when: "A query on the child association with an equals criterion is executed"
            def results = PlantCategory.withCriteria {
                plants {
                    eq 'name', 'Mango'
                }
            }

        then: "Check that the Tropical plant is returned"
            results.size() == 1
            results[0].name == "Tropical"

        when: "A like criterion is executed on the child association"
            results = PlantCategory.withCriteria {
                plants {
                    like 'name', 'P%'
                }
                order "name"
            }

        then: "Check that we get 2 results back"
            results.size() == 2
            results[0].name == "Tropical"
            results[1].name == "Veg"

        when: "A not equals criterion is executed in a child association"
            results = PlantCategory.withCriteria {
                plants {
                    ne 'name', 'Carrot'
                }
                order "name"
            }

        then: "Check that we get 3 results back"
            results.size() == 3

        when: "A between query is used"
            results = PlantCategory.withCriteria {
                plants {
                    between 'id', 3L, 5L
                }
                order "name"
            }

        then: "Check we get the correct results"
            results.size() == 2
            results[0].name == "Tropical"
            results[1].name == "Veg"

        when: "A greater than query is used"
            results = PlantCategory.withCriteria {
                plants {
                    gt 'id', 5L
                }
                order "name"
            }

        then: "Check we get the correct results"
            results.size() == 2
            results[0].name == "Nuts"
            results[1].name == "Veg"

        when: "A less than query is used"
            results = PlantCategory.withCriteria {
                plants {
                    lt 'id', 5L
                }
                order "name"
            }

        then: "Check we get the correct results"
            results.size() == 2
            results[0].name == "Tropical"
            results[1].name == "Veg"

        when: "An in query is used"
            results = PlantCategory.withCriteria {
                plants {
                    inList 'name', ['Mango', 'Walnut']
                }
                order "name"
            }
        then: "We get Tropical and Nuts back"
            results.size() == 2
            results[0].name == "Nuts"
            results[1].name == "Tropical"

        when: "Multiple child criterion are used"
            results = PlantCategory.withCriteria {
                plants {
                    like 'name', 'P%'
                    eq 'goesInPatch', true
                }
                order "name"
            }

        then: "We get the expected results back"
            results.size() == 1
            results[0].name == 'Veg'

        when: "A disjunction is used"
            results = PlantCategory.withCriteria {
                plants {
                    or {
                        like 'name', 'P%'
                        eq 'goesInPatch', true
                    }
                }
                order "name"
            }

        then: "We get the expected results back"
            results.size() == 2
            results[0].name == 'Tropical'
            results[1].name == 'Veg'

        when: "A conjuntion is used"
            results = PlantCategory.withCriteria {
                plants {
                    and {
                        like 'name', 'P%'
                        eq 'goesInPatch', true
                    }
                }
                order "name"
            }

        then: "We get the expected results back"
            results.size() == 1
            results[0].name == 'Veg'
    }
}
