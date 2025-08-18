package org.grails.orm.hibernate.query;

import jakarta.persistence.FlushModeType;
import org.grails.datastore.gorm.finders.DynamicFinder;
import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.event.PostQueryEvent;
import org.grails.datastore.mapping.query.event.PreQueryEvent;
import org.grails.orm.hibernate.HibernateDatastore;
import org.grails.orm.hibernate.HibernateSession;
import org.grails.orm.hibernate.exceptions.GrailsQueryException;
import org.hibernate.FlushMode;
import org.hibernate.SessionFactory;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * A query implementation for HQL queries
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public class HibernateHqlQuery extends Query {
    private org.hibernate.query.Query query;

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


    public static HibernateHqlQuery createHqlQuery(
            org.hibernate.Session session
            , HibernateDatastore dataStore
            , SessionFactory sessionFactory
            , PersistentEntity persistentEntity
            , String sqlString
            , boolean isNative
        ) {
        var q = isNative ? session.createNativeQuery(sqlString, persistentEntity.getJavaClass()) : session.createQuery(sqlString, persistentEntity.getJavaClass());
        var hibernateSession = new HibernateSession( dataStore, sessionFactory);
        HibernateHqlQuery hibernateHqlQuery = new HibernateHqlQuery(hibernateSession, persistentEntity, q);
        hibernateHqlQuery.setFlushMode(session.getHibernateFlushMode());
        return hibernateHqlQuery;
    }

    public static HibernateHqlQuery createHqlQuery(org.hibernate.Session session
            , org.hibernate.query.Query q
            , HibernateDatastore dataStore
            , SessionFactory sessionFactory
            , PersistentEntity persistentEntity
    ) {
        var hibernateSession = new HibernateSession( dataStore, sessionFactory);
        switch (session.getHibernateFlushMode()) {
            case AUTO, ALWAYS:
                hibernateSession.setFlushMode(FlushModeType.AUTO);
                break;
            default:
                hibernateSession.setFlushMode(FlushModeType.COMMIT);
        }
        return new HibernateHqlQuery(hibernateSession, persistentEntity, q);
    }


    public void setFlushMode(FlushMode flushMode) {
        session.setFlushMode(flushMode == FlushMode.AUTO || flushMode == FlushMode.ALWAYS ?
                FlushModeType.AUTO : FlushModeType.COMMIT);
    }

    public void populateQuerySettings(Map args) {
        Optional.ofNullable(args.remove(DynamicFinder.ARGUMENT_MAX))
                .map(Object::toString)
                .map(Integer::parseInt)
                .ifPresent(query::setMaxResults);
        Optional.ofNullable(args.remove(DynamicFinder.ARGUMENT_OFFSET))
                .map(Object::toString)
                .map(Integer::parseInt)
                .ifPresent(query::setFirstResult);
        Optional.ofNullable(args.remove(DynamicFinder.ARGUMENT_CACHE))
                .map(Object::toString)
                .map(Boolean::parseBoolean)
                .ifPresent(query::setCacheable);
        Optional.ofNullable(args.remove(DynamicFinder.ARGUMENT_FETCH_SIZE))
                .map(Object::toString)
                .map(Integer::parseInt)
                .ifPresent(query::setFetchSize);
        Optional.ofNullable(args.remove(DynamicFinder.ARGUMENT_TIMEOUT))
                .map(Object::toString)
                .map(Integer::parseInt)
                .ifPresent(query::setTimeout);
        Optional.ofNullable(args.remove(DynamicFinder.ARGUMENT_READ_ONLY))
                .map(Object::toString)
                .map(Boolean::parseBoolean)
                .ifPresent(query::setReadOnly);
        Optional.ofNullable(args.remove(DynamicFinder.ARGUMENT_FLUSH_MODE))
                .filter(FlushMode.class::isInstance)
                .map(FlushMode.class::cast)
                .ifPresent(query::setHibernateFlushMode);

    }

    public void populateQueryWithNamedArguments(Map queryNamedArgs) {
        Optional.ofNullable(queryNamedArgs).ifPresent( map -> {
            map.forEach((key, value) -> {
                if (key instanceof CharSequence) {
                    String stringKey = key.toString();
                    if(value == null) {
                        query.setParameter(stringKey, null);
                    } else if (value instanceof CharSequence) {
                        query.setParameter(stringKey, value.toString());
                    } else if (List.class.isAssignableFrom(value.getClass())) {
                        query.setParameterList(stringKey, (List) value);
                    } else if (Set.class.isAssignableFrom(value.getClass())) {
                        query.setParameterList(stringKey, (Set) value);
                    } else if (value.getClass().isArray()) {
                        query.setParameterList( stringKey, (Object[]) value);
                    } else {
                        query.setParameter(stringKey, value);
                    }
                } else {
                    throw new GrailsQueryException("Named parameter's name must be String: $queryNamedArgs");
                }
            });
        });
    }

    public void populateQueryWithIndexedArguments(List params) {
        Optional.ofNullable(params).ifPresent( collection -> {
            IntStream.range(1, collection.size() + 1)
                    .forEach( index -> {
                        var val = collection.get(index - 1);
                        if (val instanceof CharSequence) {
                            query.setParameter(index, val.toString());
                        } else {
                            query.setParameter(index, val);
                        }
                    });
        });
    }


    public org.hibernate.query.Query getQuery() {
        return query;
    }

}
