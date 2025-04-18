package grails.gorm.tests

import grails.gorm.annotation.Entity
import grails.neo4j.Relationship
import grails.neo4j.mapping.MappingBuilder
import spock.lang.Issue

class ManyToManyQuerySpec extends GormDatastoreSpec {

    @Override
    List getDomainClasses() {
        [TestA, TestB, TestC]
    }

    @Issue('309')
    def "many-to-many relationships are queried correctly"() {
        setup:
        def a = new TestA(testA: "aaa").save(flush: true, failOnError: true)
        def c = new TestC(testC: "ccc").save(flush: true, failOnError: true)
        new TestB(from: a, to: c, testB: "bbb").save(flush: true, failOnError: true)

        when:
        def result = TestA.createCriteria().list {
            ccc {
                eq("testB", "bbb")
                to {
                    eq("testC", "ccc")
                }
            }
        }

        then:
        result.size() == 1
        result[0].testA == a.testA
    }
}

@Entity
class TestA {
    String testA
    static hasMany = [ccc: TestB]
}

@Entity
class TestC {
    String testC
    static hasMany = [aaa: TestB]
}

@Entity
class TestB implements Relationship<TestA, TestC> {
    String testB
    static mapping = MappingBuilder.relationship {
        type "A_REL_C"
        direction Direction.OUTGOING
    }
}
