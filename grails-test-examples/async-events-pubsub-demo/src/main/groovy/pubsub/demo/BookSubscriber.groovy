package pubsub.demo

import grails.events.annotation.Subscriber
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.engine.event.PreInsertEvent
import org.springframework.stereotype.Component

import java.util.concurrent.ConcurrentLinkedDeque

@Component
@CompileStatic
class BookSubscriber {

    List<String> newBooks = []

    @Subscriber('newBook')
    @SuppressWarnings('unused')
    void withBook(Book book) {
        newBooks.add(book.title)
    }

    // tag::gorm[]
    Collection<PreInsertEvent> insertEvents = new ConcurrentLinkedDeque<>()

    @Subscriber
    @SuppressWarnings('unused')
    void beforeInsert(PreInsertEvent event) {
        insertEvents.add(event)
    }
    // end::gorm[]
}
