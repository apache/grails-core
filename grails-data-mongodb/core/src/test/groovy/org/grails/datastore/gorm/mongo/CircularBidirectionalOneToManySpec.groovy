package org.grails.datastore.gorm.mongo

import grails.gorm.annotation.Entity
import grails.gorm.tests.GormDatastoreSpec
import spock.lang.Issue

/**
 * Created by graemerocher on 24/08/2016.
 */
class CircularBidirectionalOneToManySpec extends GormDatastoreSpec {
    @Override
    List getDomainClasses() {
        [Comment]
    }

    void "Test store and retrieve circular one-to-many association"() {
        given:"A circular one-to-many"
        new Comment(text: "Hello")
                .addToReplies(text: "World")
                .addToReplies(text: "!")
                .save(flush:true)


        session.clear()

        when:"The entity is loaded"
        def first = Comment.get(1L)

        then:"The association is valid"
        first.text == "Hello"
        first.replies.size() == 2
        first.replies.any { it.text == "World" }
        first.replies.any { it.text == "!" }

    }

    @Issue('https://github.com/grails/gorm-mongodb/issues/7')
    void "Test that deleting a child doesn't not delete the parent in a circular association"() {
        given:"A circular one-to-many"
        new Comment(text: "Hello")
                .addToReplies(text: "World")
                .addToReplies(text: "!")
                .save(flush:true)

        session.clear()

        when:"A child is deleted"
        Comment.findByText("World").delete(flush:true)
        session.clear()

        then:"The parent wasn't deleted"
        Comment.count() == 2
    }
}

@Entity
class Comment {
    Long id
    String text
    List<Comment> replies
    static belongsTo = [parent: Comment]
    static hasMany = [replies: Comment]
}