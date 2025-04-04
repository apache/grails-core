package org.grails.orm.hibernate.query;

import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.event.PostQueryEvent;
import org.grails.datastore.mapping.query.event.PreQueryEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

/**
 * A query implementation for HQL queries
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public class HibernateHqlQuery extends Query {
    private final org.hibernate.query.Query query;

    public HibernateHqlQuery(Session session, PersistentEntity entity, org.hibernate.query.Query query) {
        super(session, entity);
        this.query = query;
    }

    @Override
    protected void flushBeforeQuery() {
        // do nothing, hibernate handles this
    }

    @Override
    protected List executeQuery(PersistentEntity entity, Junction criteria) {
        Datastore datastore = getSession().getDatastore();
        ApplicationEventPublisher applicationEventPublisher = datastore.getApplicationEventPublisher();
        PreQueryEvent preQueryEvent = new PreQueryEvent(datastore, this);
        applicationEventPublisher.publishEvent(preQueryEvent);

        if(uniqueResult) {
            query.setMaxResults(1);
            List results = query.list();
            applicationEventPublisher.publishEvent(new PostQueryEvent(datastore, this, results));
            return results;
        }
        else {

            List results = query.list();
            applicationEventPublisher.publishEvent(new PostQueryEvent(datastore, this, results));
            return results;
        }
    }
}
