package org.grails.datastore.gorm

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

class CriteriaProjectedResultsSpec extends GormDatastoreSpec {

    void "Test single projection"() {
        when:"A instanced is saved"
            Check c = new Check(amount: 57).save()

        then:"The count is 1"
            1 == Check.count()

        when:"A sum projection is executed"
            def total = Check.withCriteria {
                projections {
                    sum 'amount'
                }
            }

        then:"A list is returned containing the sum"
            [57] == total
    }

    void "Test multiple projections"() {
        given:"A domain instance"
            Check c = new Check(amount: 57).save()

        when:"A projection is used with two projected results"
            def model = Check.withCriteria {
                projections {
                    rowCount()
                    sum 'amount'
                }
            }

        then:"A list containing another list is returned"
            [[1, 57]] == model
    }

    void "Test single projection multiple rows"() {
        given:"multiple records"
            new Check(amount: 57, descr: 'fifty-seven').save()
            new Check(amount: 83, descr: 'eighty-three').save()
            new Check(amount: 29, descr: 'twenty-nine').save()

        when:"A query is executed"
            def model = Check.withCriteria {
                projections {
                    property('amount')
                }
                order('amount', 'asc')
            }

        then:"A list of lists is returned"
            [29, 57, 83] == model
    }

    void "Test multiple projections with multiple rows"() {
        given:"Multiple recordes"
            new Check(amount: 57, descr: 'fifty-seven').save()
            new Check(amount: 83, descr: 'eighty-three').save()
            new Check(amount: 29, descr: 'twenty-nine').save()

        when:"A query with multiple projections is executed"
            def model = Check.withCriteria {
                projections {
                    property('descr')
                    property('amount')
                }
                order('amount', 'asc')
            }

        then:"A list of lists is returned"
            [['twenty-nine', 29], ['fifty-seven', 57], ['eighty-three', 83]] == model
    }

    void "Test single order"() {
        given:"A domain model"
            new Check(amount: 57, descr: 'fifty-seven').save()
            new Check(amount: 83, descr: 'eighty-three').save()
            new Check(amount: 29, descr: 'twenty-nine').save()
            new Check(amount: 83, descr: 'seventy').save()

        when:"The model is queried"
            def model = Check.withCriteria {
                order('amount', 'asc')
            }

        then:"The order is correct"
            assert model
            assert 4 == model.size()
            assert 29 == model[0].amount
            assert 57 == model[1].amount
            assert 83 == model[2].amount
            assert 83 == model[3].amount
    }

    void "Test multiple orders"() {
        given:"A domain model"
            new Check(amount: 57, descr: 'fifty-seven').save()
            new Check(amount: 83, descr: 'eighty-three').save()
            new Check(amount: 29, descr: 'twenty-nine').save()
            new Check(amount: 83, descr: 'seventy').save()

        when:"The domain model is queried with multiple order definitions"
            def model = Check.withCriteria {
                order('amount', 'asc')
                order('descr', 'desc')
            }

        then:"The order is correct"
            assert model
            assert 4 == model.size()
            assert 29 == model[0].amount
            assert 57 == model[1].amount
            assert 83 == model[2].amount
            assert 'seventy' == model[2].descr
            assert 83 == model[3].amount
            assert 'eighty-three' == model[3].descr
    }

    void "Test multiple orders with projections"() {
        given:"A domain model"
            new Check(amount: 57, descr: 'fifty-seven').save()
            new Check(amount: 83, descr: 'eighty-three').save()
            new Check(amount: 29, descr: 'twenty-nine').save()
            new Check(amount: 83, descr: 'seventy').save()

        when:"A query is executed with 2 projectsions and 2 orders in different directions"
            def model = Check.withCriteria {
                projections {
                    property('descr')
                    property('amount')
                }
                order('amount', 'asc')
                order('descr', 'desc')
            }
        then:"The results are correct"
            [['twenty-nine', 29], ['fifty-seven', 57], ['seventy', 83], ['eighty-three', 83]] == model

        when:"A query is executed with 2 projections and 2 orders in the same direction"
            model = Check.withCriteria {
                projections {
                    property('descr')
                    property('amount')
                }
                order('amount', 'asc')
                order('descr', 'asc')
            }

        then:"The results are correct"
            [['twenty-nine', 29], ['fifty-seven', 57], ['eighty-three', 83], ['seventy', 83]] == model

        when:"A query is executed with 2 projects and 2 orders on different properties"
            model = Check.withCriteria {
                projections {
                    property('descr')
                    property('amount')
                }
                order('amount', 'asc')
                order('id', 'desc')
            }

        then:"The results are correct"
            [['twenty-nine', 29], ['fifty-seven', 57], ['seventy', 83], ['eighty-three', 83]] == model
    }

    void testOrderAndOffset() {
        given:"A domain model"
            new Check(amount: 57, descr: 'fifty-seven').save()
            new Check(amount: 83, descr: 'eighty-three').save()
            new Check(amount: 29, descr: 'twenty-nine').save()
            new Check(amount: 83, descr: 'seventy').save()

        expect:"All items are present"
            4 == Check.count()

        when:"A query is executed with 2 orderings in different directions, offset, and limit"
            def model = Check.withCriteria {
                projections {
                    property('descr')
                    property('amount')
                }
                order('amount', 'asc')
                order('descr', 'desc')
                firstResult(1)
                maxResults(2)
            }

        then:"The results are correct"
            [['fifty-seven', 57], ['seventy', 83]] == model

        when:"A query is executed with 2 orderings in same direction, offset, and limit"
            model = Check.withCriteria {
                projections {
                    property('descr')
                    property('amount')
                }
                order('amount', 'asc')
                order('descr', 'asc')
                firstResult(2)
                maxResults(2)
            }

        then:"The results are correct"
            [['eighty-three', 83], ['seventy', 83]] == model
    }

    @Override
    List getDomainClasses() {
        [Check]
    }
}

@Entity
class Check {
    Long id
    BigDecimal amount
    String     descr

    static constraints = {
        amount nullable: false
        descr  nullable: true
    }
}
