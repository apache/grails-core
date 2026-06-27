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

import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import groovy.lang.Closure;

import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;

import org.hibernate.FlushMode;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.QueryFlushMode;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaCriteriaQuery;
import org.hibernate.query.criteria.JpaSubQuery;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.convert.ConversionService;
import org.springframework.dao.InvalidDataAccessApiUsageException;

import grails.gorm.DetachedCriteria;
import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.proxy.ProxyHandler;
import org.grails.datastore.mapping.query.AssociationQuery;
import org.grails.datastore.mapping.query.Projections;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.api.QueryableCriteria;
import org.grails.datastore.mapping.query.event.PostQueryEvent;
import org.grails.datastore.mapping.query.event.PreQueryEvent;
import org.grails.orm.hibernate.GrailsHibernateTemplate;
import org.grails.orm.hibernate.HibernateSession;
import org.grails.orm.hibernate.IHibernateTemplate;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.proxy.HibernateProxyHandler;

/**
 * Bridges the Query API with the Hibernate Criteria API
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("rawtypes")
public class HibernateQuery extends Query {

    protected static final String ALIAS = "_alias";
    private final Map<String, CriteriaAndAlias> createdAssociationPaths = new HashMap<>();
    private final List<HibernateAlias> aliases = new java.util.ArrayList<>();
    private final Set<String> fetchJoinPaths = new LinkedHashSet<>();
    protected String alias;
    protected int aliasCount;
    protected Deque<GrailsHibernatePersistentEntity> entityStack = new LinkedList<>();
    protected Deque<Association> associationStack = new LinkedList<>();
    protected DetachedCriteria<?> detachedCriteria;
    protected ProxyHandler proxyHandler = new HibernateProxyHandler();
    private Integer fetchSize;
    private Integer timeout;
    private QueryFlushMode flushMode;
    private Boolean readOnly;

    public HibernateQuery(HibernateSession session, GrailsHibernatePersistentEntity entity) {
        super(session, entity);
        this.detachedCriteria = new DetachedCriteria<>(entity.getJavaClass());
        this.jpaProjectionList = new JpaProjectionList();
        this.projections = jpaProjectionList;
    }

    public GrailsHibernateTemplate getHibernateTemplate() {
        return ((HibernateSession) getSession()).getHibernateTemplate();
    }

    public DetachedCriteria<?> getDetachedCriteria() {
        return detachedCriteria;
    }

    public void setDetachedCriteria(DetachedCriteria<?> detachedCriteria) {
        this.detachedCriteria = detachedCriteria;
    }

    public List<HibernateAlias> getAliases() {
        return Collections.unmodifiableList(aliases);
    }

    public void addAlias(HibernateAlias alias) {
        this.aliases.add(alias);
    }

    @Override
    protected Object resolveIdIfEntity(Object value) {
        // for Hibernate queries, the object itself is used in queries, not the id
        return value;
    }

    @Override
    public Query isEmpty(String property) {
        detachedCriteria.isEmpty(calculatePropertyName(property));
        return this;
    }

    @Override
    public Query isNotEmpty(String property) {
        detachedCriteria.isNotEmpty(calculatePropertyName(property));
        return this;
    }

    public Query count() {
        projections.count();
        return this;
    }

    @Override
    public Query isNull(String property) {
        detachedCriteria.isNull(calculatePropertyName(property));
        return this;
    }

    @Override
    public Query isNotNull(String property) {
        detachedCriteria.isNotNull(calculatePropertyName(property));
        return this;
    }

    @Override
    public GrailsHibernatePersistentEntity getEntity() {
        if (!entityStack.isEmpty()) {
            return entityStack.getLast();
        }
        return (GrailsHibernatePersistentEntity) super.getEntity();
    }

    private String getAssociationPath(String propertyName) {
        if (propertyName.indexOf('.') > -1) {
            return propertyName;
        } else {
            StringBuilder fullPath = new StringBuilder();
            for (Association association : associationStack) {
                fullPath.append(association.getName());
                fullPath.append('.');
            }
            fullPath.append(propertyName);
            return fullPath.toString();
        }
    }

    public List<Criterion> getAllCriteria() {
        return detachedCriteria.getCriteria();
    }

    @Override
    public Junction disjunction() {
        Disjunction dis = new Disjunction();
        detachedCriteria.add(dis);
        return dis;
    }

    @Override
    public Junction conjunction() {
        Conjunction con = new Conjunction();
        detachedCriteria.add(con);
        return con;
    }

    public void add(Criterion criterion) {
        detachedCriteria.add(criterion);
    }

    public void add(DetachedCriteria<?> detachedCriteria) {
        detachedCriteria.add(new Conjunction(detachedCriteria.getCriteria()));
    }

    @Override
    public void add(Junction currentJunction, Criterion criterion) {
        Disjunction disjunction = (Disjunction) detachedCriteria.getCriteria().stream()
                .filter(it -> it instanceof Disjunction)
                .findFirst()
                .orElse(new Disjunction());
        disjunction.add(criterion);
        detachedCriteria.add(disjunction);
    }

    @Override
    public Query eq(String property, Object value) {
        detachedCriteria.eq(calculatePropertyName(property), value);
        return this;
    }

    @Override
    public Query idEq(Object value) {
        detachedCriteria.idEq(value);
        return this;
    }

    @Override
    public Query gt(String property, Object value) {
        detachedCriteria.gt(calculatePropertyName(property), value);
        return this;
    }

    @Override
    public Query and(Criterion a, Criterion b) {
        and(List.of(a, b));
        return this;
    }

    public Query and(List<Criterion> criteria) {
        var conjunction = new Conjunction();
        criteria.forEach(conjunction::add);
        detachedCriteria.add(conjunction);
        return this;
    }

    public Query and(Closure closure) {
        detachedCriteria.and(closure);
        return this;
    }

    @Override
    public Query or(Criterion a, Criterion b) {
        or(List.of(a, b));
        return this;
    }

    public Query or(List<Criterion> criteria) {
        var disjunction = new Disjunction();
        criteria.forEach(disjunction::add);
        detachedCriteria.add(disjunction);
        return this;
    }

    public Query or(Closure closure) {
        detachedCriteria.or(closure);
        return this;
    }

    public Query not(Criterion a) {
        not(new Closure(HibernateQuery.this) {
            @SuppressWarnings("unused") // called reflectively by the Groovy runtime as the closure body
            public void doCall() {
                ((DetachedCriteria) getDelegate()).add(a);
            }
        });
        return this;
    }

    public Query not(List<Criterion> criteria) {
        var conjunction = new Conjunction();
        criteria.forEach(conjunction::add);
        var negation = new Negation();
        negation.add(conjunction);
        detachedCriteria.add(negation);
        return this;
    }

    public Query not(Closure closure) {
        detachedCriteria.not(closure);
        return this;
    }

    @Override
    public Query allEq(Map<String, Object> values) {
        values.forEach((key, value) -> {
            if (value == null) {
                detachedCriteria.isNull(calculatePropertyName(key));
            } else {
                detachedCriteria.eq(calculatePropertyName(key), value);
            }
        });
        return this;
    }

    @Override
    public Query ge(String property, Object value) {
        detachedCriteria.ge(calculatePropertyName(property), value);
        return this;
    }

    @Override
    public Query le(String property, Object value) {
        detachedCriteria.le(calculatePropertyName(property), value);
        return this;
    }

    @Override
    public Query gte(String property, Object value) {
        detachedCriteria.gte(calculatePropertyName(property), value);
        return this;
    }

    @Override
    public Query lte(String property, Object value) {
        detachedCriteria.lte(calculatePropertyName(property), value);
        return this;
    }

    @Override
    public Query lt(String property, Object value) {
        detachedCriteria.lt(calculatePropertyName(property), value);
        return this;
    }

    @Override
    public Query in(String property, List values) {
        detachedCriteria.in(calculatePropertyName(property), values);
        return this;
    }

    @Override
    public Query between(String property, Object start, Object end) {
        detachedCriteria.between(calculatePropertyName(property), start, end);
        return this;
    }

    @Override
    public Query like(String property, String expr) {
        detachedCriteria.like(calculatePropertyName(property), expr);
        return this;
    }

    @Override
    public Query ilike(String property, String expr) {
        detachedCriteria.ilike(calculatePropertyName(property), expr);
        return this;
    }

    @Override
    public Query rlike(String property, String expr) {
        detachedCriteria.rlike(calculatePropertyName(property), expr);
        return this;
    }

    @Override
    public AssociationQuery createQuery(String associationName) {
        final PersistentProperty property =
                ((GrailsHibernatePersistentEntity) entity).getPropertyByName(calculatePropertyName(associationName));
        if ((property instanceof Association association)) {
            String alias = generateAlias(associationName);
            CriteriaAndAlias subCriteria = getOrCreateAlias(associationName, alias);
            return new HibernateAssociationQuery(
                    (HibernateSession) getSession(),
                    (GrailsHibernatePersistentEntity) association.getAssociatedEntity(),
                    association,
                    subCriteria.associationPath,
                    alias);
        }
        throw new InvalidDataAccessApiUsageException(
                "Cannot query association [" + calculatePropertyName(associationName) +
                        "] of entity [" +
                        entity +
                        "]. Property is not an association!");
    }

    @SuppressWarnings("PMD.DataflowAnomalyAnalysis")
    private CriteriaAndAlias getOrCreateAlias(String associationName, String alias) {
        String associationPath = getAssociationPath(associationName);
        String effectiveAlias = (alias == null) ? generateAlias(associationName) : alias;

        if (createdAssociationPaths.containsKey(associationPath)) {
            return createdAssociationPaths.get(associationPath);
        } else {
            CriteriaQuery criteriaQuery = getCriteriaBuilder().createQuery(entity.getJavaClass());
            CriteriaAndAlias subCriteria = new CriteriaAndAlias(criteriaQuery, effectiveAlias, associationPath);
            createdAssociationPaths.put(associationPath, subCriteria);
            createdAssociationPaths.put(effectiveAlias, subCriteria);
            return subCriteria;
        }
    }

    @Override
    public Query firstResult(int offset) {
        offset(offset);
        return this;
    }

    @Override
    public Query cache(boolean cache) {
        return super.cache(cache);
    }

    @Override
    public Query lock(boolean lock) {
        return super.lock(lock);
    }

    @Override
    public Query order(Order order) {
        detachedCriteria.order(order);
        return this;
    }

    @Override
    public Query clearOrders() {
        detachedCriteria.getOrders().clear();
        super.clearOrders();
        return this;
    }

    @Override
    public Query join(String property) {
        fetchJoinPaths.add(property);
        detachedCriteria.join(property);
        return this;
    }

    @Override
    public Query join(String property, JoinType joinType) {
        fetchJoinPaths.add(property);
        detachedCriteria.join(property, joinType);
        return this;
    }

    /**
     * The association paths requested as eager join fetches via {@link #join(String)} - for
     * example by a dynamic finder invoked with {@code [fetch: [assoc: 'join']]}. These are
     * materialized as JPA {@code root.fetch(...)} joins so the associations are eagerly
     * initialized, matching the Hibernate 5 behaviour.
     */
    public Set<String> getFetchJoinPaths() {
        return Collections.unmodifiableSet(fetchJoinPaths);
    }

    @Override
    public Query select(String property) {
        detachedCriteria.select(property);
        // Ensure property is added to projections for Hibernate 7
        projections.property(property);
        return this;
    }

    @Override
    public List list() {
        firePreQueryEvent();
        List results = executeList();
        return firePostQueryEvent(results);
    }

    private List executeList() {
        return getHibernateQueryExecutor().list(getCurrentSession(), getJpaCriteriaQuery());
    }

    public List list(Session session) {
        return getHibernateQueryExecutor().list(session, getJpaCriteriaQuery());
    }

    private HibernateQueryExecutor getHibernateQueryExecutor() {
        return new HibernateQueryExecutor(
                offset, max, lockResult, queryCache, fetchSize, timeout, flushMode, readOnly, proxyHandler);
    }

    public JpaCriteriaQuery<?> getJpaCriteriaQuery() {
        ConversionService conversionService = getSession().getMappingContext().getConversionService();
        return new JpaCriteriaQueryCreator(
                        projections, getCriteriaBuilder(), (GrailsHibernatePersistentEntity) entity, detachedCriteria, conversionService, this)
                .createQuery();
    }

    public void setFetchSize(Integer fetchSize) {
        this.fetchSize = fetchSize;
    }

    @Override
    protected void flushBeforeQuery() {
        // do nothing
    }

    @Override
    public Object singleResult() {
        firePreQueryEvent();
        Object result = executeSingleResult();
        return firePostQueryEvent(result);
    }

    private Object executeSingleResult() {
        return getHibernateQueryExecutor().singleResult(getCurrentSession(), getJpaCriteriaQuery());
    }

    public Object singleResult(Session session) {
        return getHibernateQueryExecutor().singleResult(session, getJpaCriteriaQuery());
    }

    @Override
    public Number countResults() {
        firePreQueryEvent();

        Number result;
        if (projections.getProjectionList().isEmpty()) {
            projections().count();
            result = (Number) executeSingleResult();
        } else {
            HibernateCriteriaBuilder cb = getCriteriaBuilder();

            JpaCriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
            JpaSubQuery<Tuple> innerSubquery = countQuery.subquery(Tuple.class);

            ConversionService cs = getSession().getMappingContext().getConversionService();
            new JpaCriteriaQueryCreator(projections, cb, (GrailsHibernatePersistentEntity) entity, detachedCriteria, cs).populateSubquery(innerSubquery);

            countQuery.from(innerSubquery);
            countQuery.select(cb.count(cb.literal(1)));
            result = (Number) getHibernateQueryExecutor().singleResult(getCurrentSession(), countQuery);
        }

        return (Number) firePostQueryEvent(result);
    }

    private void firePreQueryEvent() {
        Datastore datastore = session.getDatastore();
        ApplicationEventPublisher publisher = datastore.getApplicationEventPublisher();
        if (publisher != null) {
            publisher.publishEvent(new PreQueryEvent(datastore, this));
        }
    }

    private List firePostQueryEvent(List results) {
        Datastore datastore = session.getDatastore();
        ApplicationEventPublisher publisher = datastore.getApplicationEventPublisher();
        if (publisher != null) {
            PostQueryEvent postQueryEvent = new PostQueryEvent(datastore, this, results);
            publisher.publishEvent(postQueryEvent);
            return postQueryEvent.getResults();
        }
        return results;
    }

    private Object firePostQueryEvent(Object result) {
        List<?> results = firePostQueryEvent(Collections.singletonList(result));
        return results.isEmpty() ? null : results.get(0);
    }

    public Object scroll() {
        firePreQueryEvent();
        return getHibernateQueryExecutor().scroll(getCurrentSession(), getJpaCriteriaQuery());
    }

    public Object scroll(Session session) {
        return getHibernateQueryExecutor().scroll(session, getJpaCriteriaQuery());
    }

    private Session getCurrentSession() {
        return getSessionFactory().getCurrentSession();
    }

    private SessionFactory getSessionFactory() {
        return ((IHibernateTemplate) session.getNativeInterface()).getSessionFactory();
    }

    public HibernateCriteriaBuilder getCriteriaBuilder() {
        return getSessionFactory().getCriteriaBuilder();
    }

    @Override
    protected List executeQuery(PersistentEntity entity, Junction criteria) {
        return list();
    }

    protected String calculatePropertyName(String property) {
        if (alias == null) {
            return property;
        }
        return alias + '.' + property;
    }

    protected String generateAlias(String associationName) {
        return calculatePropertyName(associationName) + calculatePropertyName(ALIAS) + aliasCount++;
    }

    public Query in(String propertyName, QueryableCriteria<?> subquery) {
        detachedCriteria.inList(calculatePropertyName(propertyName), subquery);
        return this;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public void setHibernateFlushMode(FlushMode flushMode) {
        this.flushMode = GrailsQueryFlushMode.mapToHibernateQueryFlushMode(flushMode);
    }

    public void setReadOnly(Boolean readOnly) {
        this.readOnly = readOnly;
    }

    public DetachedCriteria<?> getHibernateCriteria() {
        return detachedCriteria;
    }

    public Query notIn(String propertyName, QueryableCriteria<?> subquery) {
        detachedCriteria.notIn(calculatePropertyName(propertyName), subquery);
        return this;
    }

    public Query exists(QueryableCriteria<?> subquery) {
        detachedCriteria.exists(subquery);
        return this;
    }

    public Query notExits(QueryableCriteria<?> subquery) {
        detachedCriteria.notExists(subquery);
        return this;
    }

    public Query gtAll(String propertyName, QueryableCriteria<?> subquery) {
        detachedCriteria.gtAll(calculatePropertyName(propertyName), subquery);
        return this;
    }

    public Query geAll(String propertyName, QueryableCriteria<?> subquery) {
        detachedCriteria.geAll(calculatePropertyName(propertyName), subquery);
        return this;
    }

    public Query ltAll(String propertyName, QueryableCriteria<?> subquery) {
        detachedCriteria.ltAll(calculatePropertyName(propertyName), subquery);
        return this;
    }

    public Query leAll(String propertyName, QueryableCriteria<?> subquery) {
        detachedCriteria.leAll(calculatePropertyName(propertyName), subquery);
        return this;
    }

    public Query gtSome(String propertyName, QueryableCriteria<?> subquery) {
        detachedCriteria.gtSome(calculatePropertyName(propertyName), subquery);
        return this;
    }

    public Query geSome(String propertyName, QueryableCriteria<?> subquery) {
        detachedCriteria.geSome(calculatePropertyName(propertyName), subquery);
        return this;
    }

    public Query ltSome(String propertyName, QueryableCriteria<?> subquery) {
        detachedCriteria.ltSome(calculatePropertyName(propertyName), subquery);
        return this;
    }

    public Query leSome(String propertyName, QueryableCriteria<?> subquery) {
        detachedCriteria.leSome(calculatePropertyName(propertyName), subquery);
        return this;
    }

    public Query eqAll(String propertyName, QueryableCriteria propertyValue) {
        detachedCriteria.eqAll(calculatePropertyName(propertyName), propertyValue);
        return this;
    }

    public Query ne(String propertyName, Object propertyValue) {
        detachedCriteria.ne(calculatePropertyName(propertyName), propertyValue);
        return this;
    }

    public Query eqProperty(String propertyName, String otherPropertyName) {
        detachedCriteria.eqProperty(calculatePropertyName(propertyName), otherPropertyName);
        return this;
    }

    public Query neProperty(String propertyName, String otherPropertyName) {
        detachedCriteria.neProperty(calculatePropertyName(propertyName), otherPropertyName);
        return this;
    }

    public Query gtProperty(String propertyName, String otherPropertyName) {
        detachedCriteria.gtProperty(calculatePropertyName(propertyName), otherPropertyName);
        return this;
    }

    public Query geProperty(String propertyName, String otherPropertyName) {
        detachedCriteria.geProperty(calculatePropertyName(propertyName), otherPropertyName);
        return this;
    }

    public Query ltProperty(String propertyName, String otherPropertyName) {
        detachedCriteria.ltProperty(calculatePropertyName(propertyName), otherPropertyName);
        return this;
    }

    public Query leProperty(String propertyName, String otherPropertyName) {
        detachedCriteria.leProperty(calculatePropertyName(propertyName), otherPropertyName);
        return this;
    }

    public Query sizeEq(String propertyName, int size) {
        detachedCriteria.sizeEq(calculatePropertyName(propertyName), size);
        return this;
    }

    public Query sizeGt(String propertyName, int size) {
        detachedCriteria.sizeGt(calculatePropertyName(propertyName), size);
        return this;
    }

    public Query sizeGe(String propertyName, int size) {
        detachedCriteria.sizeGe(calculatePropertyName(propertyName), size);
        return this;
    }

    public Query sizeLe(String propertyName, int size) {
        detachedCriteria.sizeLe(calculatePropertyName(propertyName), size);
        return this;
    }

    public Query sizeLt(String propertyName, int size) {
        detachedCriteria.sizeLt(calculatePropertyName(propertyName), size);
        return this;
    }

    protected JpaProjectionList jpaProjectionList;

    @Override
    public ProjectionList projections() {
        return jpaProjectionList;
    }

    protected class JpaProjectionList extends ProjectionList {

        @Override
        public ProjectionList add(Projection p) {
            super.add(p);
            return this;
        }

        @Override
        public org.grails.datastore.mapping.query.api.ProjectionList countDistinct(String property) {
            add(Projections.countDistinct(property));
            return this;
        }

        @Override
        public org.grails.datastore.mapping.query.api.ProjectionList distinct(String property) {
            add(Projections.distinct(property));
            return this;
        }

        @Override
        public org.grails.datastore.mapping.query.api.ProjectionList rowCount() {
            return count();
        }

        @Override
        public ProjectionList id() {
            add(Projections.id());
            return this;
        }

        @Override
        public ProjectionList count() {
            add(Projections.count());
            return this;
        }

        @Override
        public ProjectionList property(String name) {
            add(Projections.property(name));
            return this;
        }

        @Override
        public ProjectionList sum(String name) {
            add(Projections.sum(name));
            return this;
        }

        @Override
        public ProjectionList min(String name) {
            add(Projections.min(name));
            return this;
        }

        @Override
        public ProjectionList max(String name) {
            add(Projections.max(name));
            return this;
        }

        @Override
        public ProjectionList avg(String name) {
            add(Projections.avg(name));
            return this;
        }

        @Override
        public ProjectionList distinct() {
            add(Projections.distinct());
            return this;
        }
    }

    public Query sizeNe(String propertyName, int size) {
        detachedCriteria.sizeNe(calculatePropertyName(propertyName), size);
        return this;
    }

    @Override
    public Query maxResults(int maxResults) {
        this.max = maxResults;
        return this;
    }

    public Query distinct() {
        projections.add(Projections.distinct());
        return this;
    }

    @Override
    @SuppressWarnings({
        "PMD.CloneThrowsCloneNotSupportedException",
        "CloneDoesntCallSuperClone" // intentional: constructs a fresh instance via the session template
        // to avoid shallow-copying the live Session and DetachedCriteria state
    })
    public HibernateQuery clone() {
        final HibernateSession hibernateSession = (HibernateSession) getSession();
        final GrailsHibernateTemplate hibernateTemplate =
                (GrailsHibernateTemplate) hibernateSession.getNativeInterface();
        return (HibernateQuery)
                hibernateTemplate.execute((GrailsHibernateTemplate.HibernateCallback<Object>) session -> {
                    HibernateQuery hibernateQuery = new HibernateQuery(hibernateSession, (GrailsHibernatePersistentEntity) entity);
                    if (this.max != null && this.max > 0) {
                        hibernateQuery.max(this.max);
                    }
                    if (this.offset != null && this.offset > 0) {
                        hibernateQuery.offset(this.offset);
                    }
                    hibernateQuery.setDetachedCriteria(this.detachedCriteria.clone());

                    return hibernateQuery;
                });
    }
}
