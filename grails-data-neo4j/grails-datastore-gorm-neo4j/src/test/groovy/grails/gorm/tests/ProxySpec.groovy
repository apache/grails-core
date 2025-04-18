package grails.gorm.tests

import grails.gorm.annotation.Entity


class ProxySpec extends GormDatastoreSpec {

    @Override
    List getDomainClasses() {
        return [Owner, RockClub, Faculty, Student, SchoolEntity]
    }

    void "test invokeEntityProxyMethods proxy id for ToOne association"() {
        given:
        bootstrapData()
        session.flush()
        session.clear()

        expect:
        RockClub.findByName("GR8 Club").owner.id ==
                Owner.findByName("John Doe2").id
    }


    void "test getPropertyBeforeResolving proxy for ToOne association"() {
        given:
        bootstrapData2()
        session.flush()
        session.clear()

        expect:
        Student.findByName("Bruce").teacher.id ==
                Faculty.findByName("John Doe2").id
    }

    void bootstrapData() {
        new Owner(name: "John Doe1").save()
        Owner owner = new Owner(name: "John Doe2").save()
        new RockClub(name: "GR8 Club", owner: owner).save(flush: true)
    }

    void bootstrapData2() {
        Faculty faculty2 = new Faculty(name: "John Doe2", speciality: "Science").save()
        new Faculty(name: "John Doe1", speciality: "Mathematics").save()
        new Student(name: "Tim", rank: 1).save()
        new Student(name: "Bruce", teacher: faculty2, rank: 2).save(flush: true)

    }
}

@Entity
abstract class SchoolEntity {
    String name
}

@Entity
class Owner {
    String name
}

@Entity
class RockClub {
    String name
    Owner owner
}


@Entity
class Student extends SchoolEntity {
    Integer rank
    Faculty teacher
}

@Entity
class Faculty extends SchoolEntity {
    String speciality
}