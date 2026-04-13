/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.orm.hibernate.query;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.persistence.FlushModeType;

import org.hibernate.FlushMode;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.QueryFlushMode;

import org.springframework.core.convert.ConversionService;

import org.grails.datastore.mapping.query.Query;
import org.grails.orm.hibernate.GrailsHibernateTemplate;
import org.grails.orm.hibernate.HibernateDatastore;
import org.grails.orm.hibernate.HibernateSession;
import org.grails.orm.hibernate.IHibernateTemplate;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;

/**
 * Bridges the Query API with the Hibernate HQL for SELECT queries.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("rawtypes")
//TODO CLEANUP
public class HibernateHqlQuery extends Query {

    protected static final Set<String> INTERNAL_SETTINGS = Set.of(
            HibernateQueryArgument.FLUSH_MODE.value(),
            HibernateQueryArgument.CACHE.value(),
            HibernateQueryArgument.TIMEOUT.value(),
            HibernateQueryArgument.READ_ONLY.value(),
            HibernateQueryArgument.FETCH_SIZE.value(),
            HibernateQueryArgument.MAX.value(),
            HibernateQueryArgument.OFFSET.value()
    );

    protected final HqlQueryContext queryContext;
    protected final HqlQueryDelegate delegate;
    protected final String hql;

    public HibernateHqlQuery(HibernateSession session, GrailsHibernatePersistentEntity entity, HqlQueryContext queryContext) {
        super(session, entity);
        this.queryContext = queryContext;
        this.delegate = null;
        this.hql = null;
    }

    public HibernateHqlQuery(HibernateSession session, GrailsHibernatePersistentEntity entity, HqlQueryContext queryContext, HqlQueryDelegate delegate) {
        super(session, entity);
        this.queryContext = queryContext;
        this.delegate = delegate;
        this.hql = null;
    }

    public HibernateHqlQuery(HibernateSession session, GrailsHibernatePersistentEntity entity, String hql) {
        super(session, entity);
        this.queryContext = HqlQueryContext.prepare(entity, hql, null, null, null, false, false);
        this.delegate = null;
        this.hql = hql;
    }

    public HibernateHqlQuery(HibernateSession session, GrailsHibernatePersistentEntity entity, org.hibernate.query.Query query) {
        super(session, entity);
        this.queryContext = null;
        this.delegate = new SelectQueryDelegate(query);
        this.hql = query.getQueryString();
    }

    public static Query createHqlQuery(
            HibernateDatastore datastore,
            SessionFactory sessionFactory,
            org.grails.datastore.mapping.model.PersistentEntity entity,
            HqlQueryContext ctx,
            GrailsHibernateTemplate hibernateTemplate,
            ConversionService conversionService) {

        HibernateSession hibernateSession = (HibernateSession) datastore.getCurrentSession();
        if (ctx.isUpdate()) {
            return new MutationHqlQuery(hibernateSession, (GrailsHibernatePersistentEntity) entity, ctx);
        } else {
            return new HibernateHqlQuery(hibernateSession, (GrailsHibernatePersistentEntity) entity, ctx);
        }
    }

    public static Query buildQuery(
            org.hibernate.Session session,
            HibernateDatastore dataStore,
            SessionFactory sessionFactory,
            org.grails.datastore.mapping.model.PersistentEntity entity,
            HqlQueryContext ctx) {
        HibernateSession hibernateSession = new HibernateSession(dataStore, sessionFactory);
        String hqlStr = ctx.hql();
        if (ctx.isUpdate()) {
            org.hibernate.query.MutationQuery mq = session.createMutationQuery(hqlStr);
            return new MutationHqlQuery(hibernateSession, (GrailsHibernatePersistentEntity) entity, ctx, new MutationQueryDelegate(mq));
        } else {
            org.hibernate.query.Query q = ctx.isNative() ?
                    session.createNativeQuery(hqlStr, ctx.targetClass()) :
                    (ctx.targetClass() != null ? session.createQuery(hqlStr, ctx.targetClass()) : session.createQuery(hqlStr));
            return new HibernateHqlQuery(hibernateSession, (GrailsHibernatePersistentEntity) entity, ctx, new SelectQueryDelegate(q));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List list() {
        GrailsHibernateTemplate template = (GrailsHibernateTemplate) getHibernateTemplate();
        return template.execute(session -> {
            HqlQueryDelegate d = getOrCreateDelegate(session);
            applyQuerySettings(d);
            return d.list();
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object singleResult() {
        GrailsHibernateTemplate template = (GrailsHibernateTemplate) getHibernateTemplate();
        return template.execute(session -> {
            HqlQueryDelegate d = getOrCreateDelegate(session);
            applyQuerySettings(d);
            List results = d.list();
            return results.isEmpty() ? null : results.get(0);
        });
    }

    protected HqlQueryDelegate getOrCreateDelegate(Session session) {
        if (delegate != null) {
            return delegate;
        }
        org.hibernate.query.Query q;
        if (queryContext != null) {
            if (queryContext.isNative()) {
                q = session.createNativeQuery(queryContext.hql(), queryContext.targetClass());
            } else {
                q = queryContext.targetClass() != null ?
                        session.createQuery(queryContext.hql(), queryContext.targetClass()) :
                        session.createQuery(queryContext.hql());
            }
            return new SelectQueryDelegate(q);
        }
        return new SelectQueryDelegate(session.createQuery(hql, entity.getJavaClass()));
    }

    protected void applyQuerySettings(HqlQueryDelegate d) {
        if (max != null && max > -1) {
            d.setMaxResults(max);
        }
        if (offset != null && offset > -1) {
            d.setFirstResult(offset);
        }
        if (queryContext != null) {
            populateQuerySettings(d, queryContext.querySettings());
            populateParameters(d);
        }
    }

    protected void populateQuerySettings(HqlQueryDelegate d, Map<String, Object> args) {
        if (args == null || args.isEmpty()) return;
        if (args.containsKey(HibernateQueryArgument.MAX.value())) {
            d.setMaxResults((Integer) args.get(HibernateQueryArgument.MAX.value()));
        }
        if (args.containsKey(HibernateQueryArgument.OFFSET.value())) {
            d.setFirstResult((Integer) args.get(HibernateQueryArgument.OFFSET.value()));
        }
        if (args.containsKey(HibernateQueryArgument.READ_ONLY.value())) {
            d.setReadOnly((Boolean) args.get(HibernateQueryArgument.READ_ONLY.value()));
        }
        if (args.containsKey(HibernateQueryArgument.FLUSH_MODE.value())) {
            d.setQueryFlushMode(convertQueryFlushMode(args.get(HibernateQueryArgument.FLUSH_MODE.value())));
        }
    }

    protected void populateParameters(HqlQueryDelegate d) {
        if (queryContext.namedParams() != null && !queryContext.namedParams().isEmpty()) {
            queryContext.namedParams().forEach((key, value) -> {
                if (INTERNAL_SETTINGS.contains(key)) return;
                Object val = convertValue(value);
                if (val instanceof Collection) {
                    d.setParameterList(key, (Collection) val);
                } else if (val != null && val.getClass().isArray()) {
                    d.setParameterList(key, (Object[]) val);
                } else {
                    d.setParameter(key, val);
                }
            });
        } else if (queryContext.positionalParams() != null && !queryContext.positionalParams().isEmpty()) {
            for (int i = 0; i < queryContext.positionalParams().size(); i++) {
                Object val = convertValue(queryContext.positionalParams().get(i));
                d.setParameter(i + 1, val);
            }
        }
    }

    protected static Object convertValue(Object value) {
        if (value instanceof CharSequence) {
            return value.toString();
        }
        if (value instanceof Collection coll) {
            List<Object> newList = new ArrayList<>(coll.size());
            for (Object o : coll) {
                newList.add(convertValue(o));
            }
            return newList;
        }
        if (value != null && value.getClass().isArray()) {
            int length = Array.getLength(value);
            Object newArray = Array.newInstance(value.getClass().getComponentType() == CharSequence.class || CharSequence.class.isAssignableFrom(value.getClass().getComponentType()) ? String.class : value.getClass().getComponentType(), length);
            for (int i = 0; i < length; i++) {
                Array.set(newArray, i, convertValue(Array.get(value, i)));
            }
            return newArray;
        }
        return value;
    }

    public int executeUpdate() {
        throw new UnsupportedOperationException("SELECT query cannot be used for executeUpdate(); use a MutationHqlQuery instead");
    }

    protected IHibernateTemplate getHibernateTemplate() {
        HibernateSession hibernateSession = (HibernateSession) getSession();
        return (IHibernateTemplate) hibernateSession.getNativeInterface();
    }

    @Override
    protected List executeQuery(org.grails.datastore.mapping.model.PersistentEntity entity, Junction criteria) {
        return list();
    }

    public static FlushMode convertFlushMode(Object object) {
        if (object == null) return null;
        if (object instanceof FlushMode) return (FlushMode) object;
        if (object instanceof QueryFlushMode qfm) {
            return convertQueryFlushModeToFlushMode(qfm);
        }
        try {
            return FlushMode.valueOf(object.toString().toUpperCase());
        } catch (IllegalArgumentException e) {
            return FlushMode.AUTO;
        }
    }

    public static QueryFlushMode convertQueryFlushMode(Object object) {
        if (object == null) {
            return QueryFlushMode.DEFAULT;
        }
        if (object instanceof QueryFlushMode) {
            return (QueryFlushMode) object;
        }
        if (object instanceof FlushMode fm) {
            return switch (fm) {
                case ALWAYS -> QueryFlushMode.FLUSH;
                case MANUAL, COMMIT -> QueryFlushMode.NO_FLUSH;
                default -> QueryFlushMode.DEFAULT;
            };
        }
        String s = object.toString().toUpperCase();
        // TODO: This needs an enum to handle these mappings properly
        if ("ALWAYS".equals(s)) {
            return QueryFlushMode.FLUSH;
        }
        if ("MANUAL".equals(s) || "COMMIT".equals(s)) {
            return QueryFlushMode.NO_FLUSH;
        }
        try {
            return QueryFlushMode.valueOf(s);
        } catch (IllegalArgumentException e) {
            return QueryFlushMode.DEFAULT;
        }
    }

    public static FlushMode convertQueryFlushModeToFlushMode(QueryFlushMode flushMode) {
        if (flushMode == null) {
            return null;
        }
        if (flushMode == QueryFlushMode.FLUSH) return FlushMode.ALWAYS;
        if (flushMode == QueryFlushMode.NO_FLUSH) return FlushMode.COMMIT;
        return FlushMode.AUTO;
    }

    public static FlushModeType convertFlushModeType(FlushMode flushMode) {
        if (flushMode == null) {
            return null;
        }
        return switch (flushMode) {
            case COMMIT -> FlushModeType.COMMIT;
            case AUTO, ALWAYS -> FlushModeType.AUTO;
            default -> null;
        };
    }

    public void setHibernateFlushMode(FlushMode flushMode) {
        // Compatibility method
    }

    public void setReadOnly(Boolean readOnly) {
        // Compatibility method
    }

    public org.hibernate.query.Query selectQuery() {
        return delegate != null ? delegate.selectQuery() : null;
    }
}
