package pubsub.demo

import grails.events.annotation.gorm.Listener
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.engine.event.PreInsertEvent
import org.springframework.stereotype.Component

@Component
@CompileStatic
class BookChecks {

    @Listener(Book)
    @SuppressWarnings('unused')
    static void checkBook(PreInsertEvent event) {
        String title = event.entityAccess.getPropertyValue('title')
        if (title?.contains('Politics')) {
            throw new IllegalArgumentException('Books about politics not allowed')
        }
    }

    @Listener(Book)
    @SuppressWarnings('unused')
    static void tagFunnyBooks(PreInsertEvent event) {
        String title = event.entityAccess.getPropertyValue('title')
        if(title?.contains('funny')) {
            event.entityAccess.setProperty('title', "Humor - ${title}".toString())
        }
    }

    @Listener(Author)
    @SuppressWarnings('unused')
    static void tagAuthor(PreInsertEvent event) {
        assert event.entityObject instanceof Author
    }
}
