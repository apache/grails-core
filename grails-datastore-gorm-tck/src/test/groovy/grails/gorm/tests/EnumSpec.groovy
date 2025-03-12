package grails.gorm.tests

import grails.persistence.Entity

import spock.lang.Issue

class EnumSpec extends GormDatastoreSpec {

    void "Test save()"() {
        given:

            EnumThing t = new EnumThing(name: 'e1', en: TestEnum.V1)

        when:
            t.save(failOnError: true, flush:true)

        then:
            t != null
            !t.hasErrors()

        when:
            t = t.get(t.id)

        then:
            t != null
            'e1' == t.name
            TestEnum.V1 == t.en
    }


    @Issue('GPMONGODB-248')
    void "Test findByInList()"() {
        given:

        new EnumThing(name: 'e1', en: TestEnum.V1).save(failOnError: true)
        new EnumThing(name: 'e2', en: TestEnum.V1).save(failOnError: true)
        new EnumThing(name: 'e3', en: TestEnum.V2).save(failOnError: true)

        EnumThing instance1
        EnumThing instance2
        EnumThing instance3

        when:
        instance1 = EnumThing.findByEnInList([TestEnum.V1])
        instance2 = EnumThing.findByEnInList([TestEnum.V2])
        instance3 = EnumThing.findByEnInList([TestEnum.V3])

        then:
        instance1 != null
        instance1.en == TestEnum.V1

        instance2 != null
        instance2.en == TestEnum.V2

        instance3 == null
    }
    void "Test findBy()"() {
        given:

            new EnumThing(name: 'e1', en: TestEnum.V1).save(failOnError: true)
            new EnumThing(name: 'e2', en: TestEnum.V1).save(failOnError: true)
            new EnumThing(name: 'e3', en: TestEnum.V2).save(failOnError: true)

            EnumThing instance1
            EnumThing instance2
            EnumThing instance3

        when:
            instance1 = EnumThing.findByEn(TestEnum.V1)
            instance2 = EnumThing.findByEn(TestEnum.V2)
            instance3 = EnumThing.findByEn(TestEnum.V3)

        then:
            instance1 != null
            instance1.en == TestEnum.V1

            instance2 != null
            instance2.en == TestEnum.V2

            instance3 == null
    }

    void "Test findBy() with clearing the session"() {
        given:

            new EnumThing(name: 'e1', en: TestEnum.V1).save(failOnError: true, flush: true)
            new EnumThing(name: 'e2', en: TestEnum.V1).save(failOnError: true, flush: true)
            new EnumThing(name: 'e3', en: TestEnum.V2).save(failOnError: true, flush: true)
            session.clear()

            EnumThing instance1
            EnumThing instance2
            EnumThing instance3

        when:
            instance1 = EnumThing.findByEn(TestEnum.V1)
            instance2 = EnumThing.findByEn(TestEnum.V2)
            instance3 = EnumThing.findByEn(TestEnum.V3)

        then:
            instance1 != null
            instance1.en == TestEnum.V1

            instance2 != null
            instance2.en == TestEnum.V2

            instance3 == null
    }

    void "Test findAllBy()"() {
        given:

            new EnumThing(name: 'e1', en: TestEnum.V1).save(failOnError: true)
            new EnumThing(name: 'e2', en: TestEnum.V1).save(failOnError: true)
            new EnumThing(name: 'e3', en: TestEnum.V2).save(failOnError: true)

            def v1Instances
            def v2Instances
            def v3Instances
            def v12Instances

        when:
            v1Instances = EnumThing.findAllByEn(TestEnum.V1)
            v2Instances = EnumThing.findAllByEn(TestEnum.V2)
            v3Instances = EnumThing.findAllByEn(TestEnum.V3)
            v12Instances = EnumThing.findAllByEnInList([TestEnum.V1, TestEnum.V2])

        then:
            v1Instances != null
            v1Instances.size() == 2
            v1Instances.every { it.en == TestEnum.V1 }

            v2Instances != null
            v2Instances.size() == 1
            v2Instances.every { it.en == TestEnum.V2 }

            v3Instances != null
            v3Instances.isEmpty()

            v12Instances != null
            v12Instances.size() == 3
    }

    void "Test countBy()"() {
        given:

            new EnumThing(name: 'e1', en: TestEnum.V1).save(failOnError: true)
            new EnumThing(name: 'e2', en: TestEnum.V1).save(failOnError: true)
            new EnumThing(name: 'e3', en: TestEnum.V2).save(failOnError: true)

            def v1Count
            def v2Count
            def v3Count

        when:
            v1Count = EnumThing.countByEn(TestEnum.V1)
            v2Count = EnumThing.countByEn(TestEnum.V2)
            v3Count = EnumThing.countByEn(TestEnum.V3)

        then:
            2 == v1Count
            1 == v2Count
            0 == v3Count
    }
}

@Entity
class EnumThing {
    Long id
    Long version
    String name
    TestEnum en

    static mapping = {
        name index: true
        en index: true
    }
}
