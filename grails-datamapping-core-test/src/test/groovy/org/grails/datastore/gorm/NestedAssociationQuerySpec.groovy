package org.grails.datastore.gorm

import jakarta.persistence.Entity
import grails.gorm.tests.GormDatastoreSpec

class NestedAssociationQuerySpec extends GormDatastoreSpec{

    void "Test that a join query can be applied to a nested association"() {
        given:"A domain multipe with nested asssociations"
            def qText = 'What is your gender?'
            def genderQuestion = createQuestion(qText)
            def femaleAnswer   = createAnswer('Female', genderQuestion)
            def userOpinion    = createUserOpinion(femaleAnswer)

        when:"The association is queried"
            def c = UserOpinion.createCriteria()
            def femaleCnt = c.count {
                answers {
                    and {
                        question {
                            eq('text', qText)
                        }
                        eq('text', 'Female')
                    }
                }
            }

        then:"The right result is returned"
            femaleCnt == 1
    }

    void "test join query for 3-level to-one association"() {
        when:"A domain model 3 levels deep is created"
            new Release(name:"Grails",milestoneCycle: new MilestoneCycle(name:"1.0",department:new Department(name:"dep A").save()).save()).save(flush:true)
            session.clear()
            Release r = Release.get(1)

        then:"The domain model is correctly populated"
            Release.count() == 1
            MilestoneCycle.count() == 1
            Department.count() == 1
            r != null
            r.milestoneCycle != null
            r.milestoneCycle.department != null

        when:"The domain model is queried"

            r = Release.createCriteria().get() {
                milestoneCycle {
                    department{
                        eq('name', 'dep A')
                    }
                }
            }

        then:"The right result is returned"
            r != null
            r.milestoneCycle != null
            r.milestoneCycle.department != null
    }

    private Question createQuestion(String qText) {
        new Question(text: qText).save(flush: true)
    }

    private Answer createAnswer(String aText, Question q) {
        new Answer(text: aText, question: q).save(flush: true)
    }

    private UserOpinion createUserOpinion(Answer answer) {
        def userOpinion = new UserOpinion()
        userOpinion.answers << answer

        userOpinion.save(flush: true)
    }

    @Override
    List getDomainClasses() {
        [UserOpinion, Answer, Question, Release, Department, MilestoneCycle]
    }
}

@grails.persistence.Entity
class UserOpinion {
    Long id
    Set answers = []
    static hasMany = [answers: Answer]
}

@grails.persistence.Entity
class Answer {

    Long id
    String text
    Question question

    static constraints = {
        text(blank: false)
    }
}

@grails.persistence.Entity
class Question {

    Long id
    String text

    static constraints = {
        text(blank: false)
    }
}

@grails.persistence.Entity
class Release {
    Long id
    String name
    MilestoneCycle milestoneCycle
}

@grails.persistence.Entity
class MilestoneCycle {
    Long id
    String name
    Department department
}

@grails.persistence.Entity
class Department {
    Long id
    String name
}
