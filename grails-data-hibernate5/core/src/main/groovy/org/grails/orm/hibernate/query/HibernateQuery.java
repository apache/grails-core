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

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.hibernate.Criteria;
import org.hibernate.FlushMode;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.function.SQLFunction;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.internal.CriteriaImpl;
import org.hibernate.persister.entity.PropertyMapping;
import org.hibernate.type.BasicType;
import org.hibernate.type.TypeResolver;

import grails.orm.HibernateCriteriaBuilder;
import grails.orm.RlikeExpression;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.query.api.QueryArgumentsAware;
import org.grails.datastore.mapping.query.api.QueryableCriteria;
import org.grails.orm.hibernate.AbstractHibernateSession;
import org.grails.orm.hibernate.GrailsHibernateTemplate;
import org.grails.orm.hibernate.HibernateSession;

/**
 * Bridges the Query API with the Hibernate Criteria API
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("rawtypes")
public class HibernateQuery extends AbstractHibernateQuery implements QueryArgumentsAware {

    /** The flush mode and the timeout of the query arguments, which a copy of the query for a count has to keep. */
    private FlushMode flushMode;
    private Integer timeout;

    public static final HibernateCriterionAdapter HIBERNATE_CRITERION_ADAPTER = new HibernateCriterionAdapter();

    public HibernateQuery(Criteria criteria, AbstractHibernateSession session, PersistentEntity entity) {
        super(criteria, session, entity);
    }

    public HibernateQuery(Criteria criteria, PersistentEntity entity) {
        super(criteria, null, entity);
    }

    public HibernateQuery(Criteria subCriteria, AbstractHibernateSession session, PersistentEntity associatedEntity, String newAlias) {
        super(subCriteria, session, associatedEntity, newAlias);
    }

    public HibernateQuery(DetachedCriteria criteria, PersistentEntity entity) {
        super(criteria, entity);
    }

    /**
     * Returns the Hibernate criteria of the query. This method makes the joins and adds the orders
     * that the criteria, projections and sorts of the query have registered so far, with the join
     * types that the query requests at that point, so that the criteria can be executed as they are.
     * Joins and orders that are registered afterwards are made when the query is executed, or by the
     * next call of this method.
     *
     * @return The hibernate criteria
     */
    public Criteria getHibernateCriteria() {
        applyDeferredJoins();
        return this.criteria;
    }

    /**
     * Applies the Hibernate settings of a GORM query arguments map to the criteria: {@code fetchSize},
     * {@code timeout}, {@code readOnly} and {@code flushMode}. Settings absent from the map are left as they are.
     * The other arguments, such as {@code max} and {@code sort}, are applied by the caller.
     *
     * @param arguments the query arguments, may be {@code null}
     * @throws IllegalArgumentException when {@code fetchSize} or {@code timeout} is not a whole number
     */
    @Override
    public void setArguments(Map arguments) {
        if (arguments == null || criteria == null) {
            return;
        }
        if (arguments.get("fetchSize") != null) {
            criteria.setFetchSize(wholeNumber("fetchSize", arguments.get("fetchSize")));
        }
        if (arguments.get("timeout") != null) {
            timeout = wholeNumber("timeout", arguments.get("timeout"));
            criteria.setTimeout(timeout);
        }
        if (arguments.get("readOnly") != null) {
            criteria.setReadOnly(Boolean.parseBoolean(String.valueOf(arguments.get("readOnly"))));
        }
        Object mode = arguments.get("flushMode");
        if (mode instanceof FlushMode) {
            flushMode = (FlushMode) mode;
            criteria.setFlushMode(flushMode);
        }
        else if (mode != null) {
            try {
                flushMode = FlushMode.valueOf(String.valueOf(mode).trim());
            }
            catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("The flushMode argument must be one of " + java.util.Arrays.toString(FlushMode.values()) + " but was [" + mode + "]", e);
            }
            criteria.setFlushMode(flushMode);
        }
    }

    /**
     * The value as an {@code int}, when it is a whole number that fits in one. A number with a fraction, such as 0.5, or one
     * outside the range of an {@code int} is refused, not cut to a different number.
     */
    private static int wholeNumber(String argument, Object value) {
        try {
            return new java.math.BigDecimal(String.valueOf(value).trim()).intValueExact();
        }
        catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("The " + argument + " argument must be a whole number but was [" + value + "]", e);
        }
    }

    /**
     * @return The hibernate detached criteria of a query that is used as a subquery. The joins of
     * the subquery are made here, once its criteria have been added and the join types that it
     * requests are known.
     */
    public DetachedCriteria getHibernateDetachedCriteria() {
        applyDeferredJoins();
        return this.detachedCriteria;
    }

    @Override
    protected AbstractHibernateCriterionAdapter createHibernateCriterionAdapter() {
        return HIBERNATE_CRITERION_ADAPTER;
    }

    protected org.hibernate.criterion.Criterion createRlikeExpression(String propertyName, String value) {
        return new RlikeExpression(propertyName, value);
    }

    protected void setDetachedCriteriaValue(QueryableCriteria value, PropertyCriterion pc) {
        DetachedCriteria hibernateDetachedCriteria = HibernateCriteriaBuilder.getHibernateDetachedCriteria(this, value);
        pc.setValue(hibernateDetachedCriteria);
    }

    protected String render(BasicType basic, List<String> columns, SessionFactory sessionFactory, SQLFunction sqlFunction) {
        return sqlFunction.render(basic, columns, (SessionFactoryImplementor) sessionFactory);
    }

    protected PropertyMapping getEntityPersister(String name, SessionFactory sessionFactory) {
        return (PropertyMapping) ((SessionFactoryImplementor) sessionFactory).getEntityPersister(name);
    }

    @Deprecated
    protected TypeResolver getTypeResolver(SessionFactory sessionFactory) {
        return ((SessionFactoryImplementor) sessionFactory).getTypeResolver();
    }

    @Deprecated
    protected Dialect getDialect(SessionFactory sessionFactory) {
        return ((SessionFactoryImplementor) sessionFactory).getDialect();
    }

    @Override
    public Object clone() {
        applyDeferredJoins();
        final CriteriaImpl impl = (CriteriaImpl) criteria;
        final HibernateSession hibernateSession = (HibernateSession) getSession();
        final GrailsHibernateTemplate hibernateTemplate = (GrailsHibernateTemplate) hibernateSession.getNativeInterface();
        return hibernateTemplate.execute((GrailsHibernateTemplate.HibernateCallback<Object>) session -> {
            Criteria newCriteria = session.createCriteria(impl.getEntityOrClassName(), impl.getAlias());

            Iterator iterator = impl.iterateExpressionEntries();
            while (iterator.hasNext()) {
                CriteriaImpl.CriterionEntry entry = (CriteriaImpl.CriterionEntry) iterator.next();
                newCriteria.add(entry.getCriterion());
            }
            Iterator subcriteriaIterator = impl.iterateSubcriteria();
            while (subcriteriaIterator.hasNext()) {
                CriteriaImpl.Subcriteria sub = (CriteriaImpl.Subcriteria) subcriteriaIterator.next();
                newCriteria.createAlias(sub.getPath(), sub.getAlias(), sub.getJoinType(), sub.getWithClause());
            }
            // the copy runs the count of a page, which has to read the data the page read: a flush mode or a timeout that the
            // query arguments gave belongs to it too
            if (flushMode != null) {
                newCriteria.setFlushMode(flushMode);
            }
            if (timeout != null) {
                newCriteria.setTimeout(timeout);
            }
            return new HibernateQuery(newCriteria, hibernateSession, entity);
        });
    }
}
