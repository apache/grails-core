package org.grails.datastore.gorm.neo4j;

import org.grails.datastore.mapping.core.Session;
import org.springframework.context.ApplicationEvent;

/**
 * event emitted when session is flushed
 */
public class SessionFlushedEvent extends ApplicationEvent {

    protected final Session session;

    public SessionFlushedEvent(Session session) {
        super(session.getDatastore());
        this.session = session;
    }

    public Session getSession() {
        return session;
    }
}
