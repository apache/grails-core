package org.grails.datastore.gorm.mongo

import grails.gorm.annotation.Entity
import grails.gorm.tests.GormDatastoreSpec
import spock.lang.Ignore
import spock.lang.Shared

class WhereQueryInCriteriaSpec extends GormDatastoreSpec {

    @Shared
    Long owner2Id

    private void buildTestData() {
        new InCritOwner(name: "Foo 1").addToDogs(name: "Chapter 1").addToDogs(name: "Chapter 2").save(flush: true, failOnError: true)
        def owner2 = new InCritOwner(name: "Foo 2").addToDogs(name: "Chapter 3").addToDogs(name: "Chapter 4").save(flush: true, failOnError: true)
        owner2Id = owner2.id
        session.clear()
    }

    void "test where query in with list on right side"() {
        given:
        buildTestData()
        List<InCritOwner> owners = InCritOwner.where {
            name in ['Foo 1']
        }.list()

        expect:
        owners.size() == 1
        owners[0].name == 'Foo 1'
    }

    void "test where query in with list of domains on right side"() {
        given:
        buildTestData()
        def ownerList = [InCritOwner.findByName('Foo 2')]
        List<InCritDog> dogs = InCritDog.where {
            owner in ownerList
        }.list()

        expect:
        dogs.size() == 2
        dogs[0].name == 'Chapter 3'
        dogs[1].name == 'Chapter 4'
    }

    void "test where query in with list of proxies on right side"() {
        given:
        buildTestData()
        def ownerList = [InCritOwner.load(owner2Id)]
        List<InCritDog> dogs = InCritDog.where {
            owner in ownerList
        }.list()

        expect:
        dogs.size() == 2
        dogs[0].name == 'Chapter 3'
        dogs[1].name == 'Chapter 4'
    }

    // MongoDB doesn't support joins so this won't work
    @Ignore
    void "test where query in with list on left side"() {
        given:
        buildTestData()
        def dogList = [InCritDog.findByName('Chapter 3'), InCritDog.findByName('Chapter 4')]
        List<InCritOwner> owners = InCritOwner.where {
            dogs in dogList
        }.list()

        expect:
        owners.size() == 1
        owners[0].name == 'Foo 2'
    }

    @Override
    List getDomainClasses() {
        [InCritOwner, InCritDog]
    }
}

@Entity
class InCritOwner {
    String name
    static hasMany = ['dogs': InCritDog]
}

@Entity
class InCritDog {
    String name
    static belongsTo = ['owner': InCritOwner]
}

