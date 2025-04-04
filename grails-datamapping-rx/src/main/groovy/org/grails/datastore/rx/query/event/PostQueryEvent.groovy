package org.grails.datastore.rx.query.event

import groovy.transform.CompileStatic
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.event.AbstractQueryEvent
import org.grails.datastore.mapping.query.event.QueryEventType
import rx.Observable

/**
 * Post query event fired by RxGORM
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class PostQueryEvent extends AbstractQueryEvent {

    Observable observable

    PostQueryEvent(Object source, Query query, Observable observable) {
        super(source, query)
        this.observable = observable
    }

    @Override
    QueryEventType getEventType() {
        QueryEventType.PostExecution
    }
}
