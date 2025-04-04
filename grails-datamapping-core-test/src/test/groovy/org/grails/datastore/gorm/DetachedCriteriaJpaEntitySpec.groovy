package org.grails.datastore.gorm

import grails.gorm.tests.GormDatastoreSpec

import jakarta.persistence.Entity

import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

import spock.lang.Issue

/**
 * ensure that detached criteria ast transformations work on annotated jpa entities
 */
@ApplyDetachedCriteriaTransform
@Issue('GRAILS-9750')
class DetachedCriteriaJpaEntitySpec extends GormDatastoreSpec {
    
    @Override
    List getDomainClasses() {
        return [Todo]
    }
    
    def "test a where query on a jpa entity"()  {
        given: "a todo"
            new Todo(title: "todo").save(flush: true)
            session.clear()

        when: "query without restrictions"
            def results = Todo.findAll {}

        then: "one todo"
            results.size() == 1

        when: "query with matching restrictions"
            results = Todo.findAll {
                title == "todo"
            }

        then: "one todo"
            results.size() == 1

        when: "query with not matching restrictions"
            results = Todo.findAll {
                title == "no match"
            }

        then: "no todo"
            results.size() == 0
    }

}

@jakarta.persistence.Entity
@grails.persistence.Entity
class Todo {
    Long id
    String title
}