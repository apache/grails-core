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
        var clazz = getTarget(sqlString,persistentEntity.getJavaClass());
        var q = isNative ? session.createNativeQuery(sqlString,clazz) : session.createQuery(sqlString, clazz);
        var hibernateSession = new HibernateSession( dataStore, sessionFactory);
        HibernateHqlQuery hibernateHqlQuery = new HibernateHqlQuery(hibernateSession, null, q);
        hibernateHqlQuery.setFlushMode(session.getHibernateFlushMode());
        return hibernateHqlQuery;
    }

    /**
     * Determine the number of top-level projections in the HQL query.
     * Returns 0 if there is no explicit SELECT clause (implicit entity projection),
     * 1 if there is a single top-level projection expression (including constructs like DISTINCT x or NEW map(...)),
     * and 2 if there are two or more top-level projection expressions (e.g. "select a, b from ...").
     *
     * Notes:
     * - Commas within parentheses or string literals are ignored.
     * - Constructor expressions like "new map(a as n, b as m)" count as a single projection.
     * - Aggregate and function calls with commas in their argument lists are handled by parentheses tracking.
     */
    static int countHqlProjections(CharSequence hql) {
        if (hql == null) return 0;
        String s = hql.toString().trim();
        if (s.isEmpty()) return 0;
        // Find select and from in a case-insensitive way
        String lower = s.toLowerCase();
        int selectIdx = lower.indexOf("select ");
        if (selectIdx < 0) {
            // no explicit select -> implicit single entity projection following "from"
            return 0;
        }
        // Ensure this select occurs before the corresponding from
        int fromIdx = lower.indexOf(" from ", selectIdx);
        if (fromIdx < 0) {
            // malformed or incomplete query; treat as one projection if select exists
            fromIdx = s.length();
        }
        int selectStart = selectIdx + "select".length();
        // Extract the select clause between 'select' and 'from'
        String sel = s.substring(selectStart, fromIdx).trim();
        if (sel.isEmpty()) return 0;
        // Strip leading DISTINCT/ALL keywords
        String selLower = sel.toLowerCase();
        if (selLower.startsWith("distinct ")) {
            sel = sel.substring("distinct ".length()).trim();
            selLower = sel.toLowerCase();
        } else if (selLower.startsWith("all ")) {
            sel = sel.substring("all ".length()).trim();
            selLower = sel.toLowerCase();
        }
        // Now count top-level commas ignoring those within parentheses and string literals
        int depth = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        int topLevelCommas = 0;
        char singleQuote = '\'';
        char doubleQuote = '"';
        char leftParen = '(';        // Left parenthesis
        char rightParen = ')';       // Right parenthesis
        char comma = ',';            // Comma



        for (int i = 0; i < sel.length(); i++) {
            char c = sel.charAt(i);
            // handle quotes (simple handling: toggle on quote not escaped by another same quote)
            if (!inDoubleQuote && c ==singleQuote) {
                // handle doubled single quotes inside strings
                if (inSingleQuote) {
                    if (i + 1 < sel.length() && sel.charAt(i + 1) == singleQuote) {
                        i++ ;// skip escaped quote
                        continue;
                    } else {
                        inSingleQuote = false;
                        continue;
                    }
                } else {
                    inSingleQuote = true;
                    continue;
                }
            }
            if (!inSingleQuote && c == doubleQuote) {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }
            if (inSingleQuote || inDoubleQuote) continue;
            if (c == leftParen) { depth++ ; continue ;}
            if (c == rightParen && depth > 0) { depth-- ; continue; }
            if (c == comma && depth == 0) { topLevelCommas++; }
        }
        if (topLevelCommas == 0) return 1;
        return 2;
    }


    static Class getTarget(CharSequence hql, Class clazz) {
        int projections = countHqlProjections(hql);
        switch(projections) {
            case 0:
                return clazz; // No explicit SELECT - implicit entity projection
            case 1:
                // Single projection - check if it's a property access (contains dot)
                if (isPropertyProjection(hql)) {
                    return Object.class; // For scalar results like "select h.name"
                } else {
                    return clazz; // For entity projections like "select h"
                }
            default:
                return Object[].class; // Multiple projections
        }
    }

    private static boolean isPropertyProjection(CharSequence hql) {
        String s = hql.toString().toLowerCase().trim();
        int selectIdx = s.indexOf("select ");
        if (selectIdx < 0) return false;

        int fromIdx = s.indexOf(" from ", selectIdx);
        if (fromIdx < 0) fromIdx = s.length();

        String selectClause = s.substring(selectIdx + "select ".length(), fromIdx).trim();

        // Remove DISTINCT/ALL if present
        if (selectClause.startsWith("distinct ")) {
            selectClause = selectClause.substring("distinct ".length()).trim();
        } else if (selectClause.startsWith("all ")) {
            selectClause = selectClause.substring("all ".length()).trim();
        }

        // Only return true for clear property projections (containing dots)
        // This is the safest approach - only treat selections with dots as scalar projections
        return selectClause.contains(".");
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
