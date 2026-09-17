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
package org.grails.orm.hibernate.query

import groovy.transform.CompileStatic
import jakarta.persistence.Tuple
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.JoinType
import org.hibernate.FlushMode
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.query.QueryFlushMode
import org.hibernate.query.criteria.HibernateCriteriaBuilder
import org.hibernate.query.criteria.JpaCriteriaQuery
import org.hibernate.query.criteria.JpaSubQuery

import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.convert.ConversionService
import org.springframework.dao.InvalidDataAccessApiUsageException

import grails.gorm.DetachedCriteria
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.proxy.ProxyHandler
import org.grails.datastore.mapping.query.AssociationQuery
import org.grails.datastore.mapping.query.Projections
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.Query.Criterion
import org.grails.datastore.mapping.query.Query.Conjunction
import org.grails.datastore.mapping.query.Query.Disjunction
import org.grails.datastore.mapping.query.Query.Junction
import org.grails.datastore.mapping.query.Query.Negation
import org.grails.datastore.mapping.query.Query.Order
import org.grails.datastore.mapping.query.Query.Projection
import org.grails.datastore.mapping.query.Query.ProjectionList
import org.grails.datastore.mapping.query.api.QueryableCriteria
import org.grails.datastore.mapping.query.event.PostQueryEvent
import org.grails.datastore.mapping.query.event.PreQueryEvent
import org.grails.orm.hibernate.GrailsHibernateTemplate
import org.grails.orm.hibernate.HibernateSession
import org.grails.orm.hibernate.IHibernateTemplate
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.proxy.HibernateProxyHandler

/**
 * Bridges the Query API with the Hibernate Criteria API
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
@SuppressWarnings('rawtypes')
class HibernateQuery extends Query {

    protected static final String ALIAS = '_alias'
    private final Map<String, CriteriaAndAlias> createdAssociationPaths = [:]
    private final List<HibernateAlias> aliases = []
    private final Set<String> fetchJoinPaths = new LinkedHashSet<>()
    protected String alias
    protected int aliasCount
    protected Deque<GrailsHibernatePersistentEntity> entityStack = new LinkedList<>()
    protected Deque<Association> associationStack = new LinkedList<>()
    protected DetachedCriteria<?> detachedCriteria
    protected ProxyHandler proxyHandler = new HibernateProxyHandler()
    private Integer fetchSize
    private Integer timeout
    private QueryFlushMode flushMode
    private Boolean readOnly
    protected JpaProjectionList jpaProjectionList

    HibernateQuery(HibernateSession session, GrailsHibernatePersistentEntity entity) {
        super(session, entity)
        this.detachedCriteria = new DetachedCriteria<>(entity.javaClass)
        this.jpaProjectionList = new JpaProjectionList()
        this.projections = jpaProjectionList
    }

    GrailsHibernateTemplate getHibernateTemplate() {
        return ((HibernateSession) session).hibernateTemplate
    }

    DetachedCriteria<?> getDetachedCriteria() {
        return detachedCriteria
    }

    void setDetachedCriteria(DetachedCriteria<?> detachedCriteria) {
        this.detachedCriteria = detachedCriteria
    }

    List<HibernateAlias> getAliases() {
        return Collections.unmodifiableList(aliases)
    }

    void addAlias(HibernateAlias alias) {
        this.aliases.add(alias)
    }

    @Override
    protected Object resolveIdIfEntity(Object value) {
        // for Hibernate queries, the object itself is used in queries, not the id
        return value
    }

    @Override
    Query isEmpty(String property) {
        detachedCriteria.isEmpty(calculatePropertyName(property))
        return this
    }

    @Override
    Query isNotEmpty(String property) {
        detachedCriteria.isNotEmpty(calculatePropertyName(property))
        return this
    }

    Query count() {
        projections.count()
        return this
    }

    @Override
    Query isNull(String property) {
        detachedCriteria.isNull(calculatePropertyName(property))
        return this
    }

    @Override
    Query isNotNull(String property) {
        detachedCriteria.isNotNull(calculatePropertyName(property))
        return this
    }

    @Override
    GrailsHibernatePersistentEntity getEntity() {
        if (!entityStack.isEmpty()) {
            return entityStack.last
        }
        return (GrailsHibernatePersistentEntity) super.entity
    }

    private String getAssociationPath(String propertyName) {
        if (propertyName.indexOf('.') > -1) {
            return propertyName
        } else {
            StringBuilder fullPath = new StringBuilder()
            for (Association association : associationStack) {
                fullPath.append(association.name)
                fullPath.append('.')
            }
            fullPath.append(propertyName)
            return fullPath.toString()
        }
    }

    List<Criterion> getAllCriteria() {
        return detachedCriteria.criteria
    }

    @Override
    void add(Criterion criterion) {
        detachedCriteria.add(criterion)
    }

    void add(DetachedCriteria<?> detachedCriteria) {
        detachedCriteria.add(new Conjunction(detachedCriteria.criteria))
    }

    @Override
    void add(Junction currentJunction, Criterion criterion) {
        Disjunction disjunction = null
        for (Criterion c : detachedCriteria.criteria) {
            if (c instanceof Disjunction) {
                disjunction = (Disjunction) c
                break
            }
        }
        if (disjunction == null) {
            disjunction = new Disjunction()
        }
        disjunction.add(criterion)
        detachedCriteria.add(disjunction)
    }

    // The factory junctions must operate on detachedCriteria (the source this query builds its JPA
    // criteria from); core's implementations add to the unused base `criteria` field, which would
    // silently drop the junction (e.g. countByXOrY losing its disjunction and counting all rows).
    @Override
    Junction disjunction() {
        Disjunction disjunction = new Disjunction()
        detachedCriteria.add(disjunction)
        return disjunction
    }

    @Override
    Junction conjunction() {
        Conjunction conjunction = new Conjunction()
        detachedCriteria.add(conjunction)
        return conjunction
    }

    @Override
    Junction negation() {
        Negation negation = new Negation()
        detachedCriteria.add(negation)
        return negation
    }

    @Override
    Query eq(String property, Object value) {
        detachedCriteria.eq(calculatePropertyName(property), value)
        return this
    }

    @Override
    Query idEq(Object value) {
        detachedCriteria.idEq(value)
        return this
    }

    @Override
    Query gt(String property, Object value) {
        detachedCriteria.gt(calculatePropertyName(property), value)
        return this
    }

    @Override
    Query and(Criterion a, Criterion b) {
        and(List.of(a, b))
        return this
    }

    Query and(List<Criterion> criteria) {
        Conjunction conjunction = new Conjunction()
        for (Criterion c : criteria) {
            conjunction.add(c)
        }
        detachedCriteria.add(conjunction)
        return this
    }

    Query and(Closure closure) {
        detachedCriteria.and(closure)
        return this
    }

    @Override
    Query or(Criterion a, Criterion b) {
        or(List.of(a, b))
        return this
    }

    Query or(List<Criterion> criteria) {
        Disjunction disjunction = new Disjunction()
        for (Criterion c : criteria) {
            disjunction.add(c)
        }
        detachedCriteria.add(disjunction)
        return this
    }

    Query or(Closure closure) {
        detachedCriteria.or(closure)
        return this
    }

    Query not(Criterion a) {
        not({ -> ((DetachedCriteria) (Object) delegate).add(a) })
        return this
    }

    Query not(List<Criterion> criteria) {
        Conjunction conjunction = new Conjunction()
        for (Criterion c : criteria) {
            conjunction.add(c)
        }
        Negation negation = new Negation()
        negation.add(conjunction)
        detachedCriteria.add(negation)
        return this
    }

    Query not(Closure closure) {
        detachedCriteria.not(closure)
        return this
    }

    @Override
    Query allEq(Map<String, Object> values) {
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            detachedCriteria.eq(calculatePropertyName(entry.key), entry.value)
        }
        return this
    }

    @Override
    Query ge(String property, Object value) {
        detachedCriteria.ge(calculatePropertyName(property), value)
        return this
    }

    @Override
    Query le(String property, Object value) {
        detachedCriteria.le(calculatePropertyName(property), value)
        return this
    }

    @Override
    Query gte(String property, Object value) {
        detachedCriteria.gte(calculatePropertyName(property), value)
        return this
    }

    @Override
    Query lte(String property, Object value) {
        detachedCriteria.lte(calculatePropertyName(property), value)
        return this
    }

    @Override
    Query lt(String property, Object value) {
        detachedCriteria.lt(calculatePropertyName(property), value)
        return this
    }

    @Override
    Query in(String property, List values) {
        detachedCriteria.in(calculatePropertyName(property), values)
        return this
    }

    @Override
    Query between(String property, Object start, Object end) {
        detachedCriteria.between(calculatePropertyName(property), start, end)
        return this
    }

    @Override
    Query like(String property, String expr) {
        detachedCriteria.like(calculatePropertyName(property), expr)
        return this
    }

    @Override
    Query ilike(String property, String expr) {
        detachedCriteria.ilike(calculatePropertyName(property), expr)
        return this
    }

    @Override
    Query rlike(String property, String expr) {
        detachedCriteria.rlike(calculatePropertyName(property), expr)
        return this
    }

    @Override
    AssociationQuery createQuery(String associationName) {
        PersistentProperty property =
                ((GrailsHibernatePersistentEntity) this.@entity).getPropertyByName(calculatePropertyName(associationName))
        if (property instanceof Association) {
            Association association = (Association) property
            String alias = generateAlias(associationName)
            CriteriaAndAlias subCriteria = getOrCreateAlias(associationName, alias)
            return new HibernateAssociationQuery(
                    (HibernateSession) session,
                    (GrailsHibernatePersistentEntity) association.associatedEntity,
                    association,
                    subCriteria.associationPath,
                    alias)
        }
        throw new InvalidDataAccessApiUsageException(
                "Cannot query association [${calculatePropertyName(associationName)}] of entity [${this.@entity}]. Property is not an association!".toString())
    }

    @SuppressWarnings('PMD.DataflowAnomalyAnalysis')
    private CriteriaAndAlias getOrCreateAlias(String associationName, String alias) {
        String associationPath = getAssociationPath(associationName)
        String effectiveAlias = (alias == null) ? generateAlias(associationName) : alias

        if (createdAssociationPaths.containsKey(associationPath)) {
            return createdAssociationPaths.get(associationPath)
        } else {
            CriteriaQuery criteriaQuery = criteriaBuilder.createQuery(this.@entity.javaClass)
            CriteriaAndAlias subCriteria = new CriteriaAndAlias(criteriaQuery, effectiveAlias, associationPath)
            createdAssociationPaths.put(associationPath, subCriteria)
            createdAssociationPaths.put(effectiveAlias, subCriteria)
            return subCriteria
        }
    }

    @Override
    Query firstResult(int offset) {
        this.offset(offset)
        return this
    }

    @Override
    Query cache(boolean cache) {
        return super.cache(cache)
    }

    @Override
    Query lock(boolean lock) {
        return super.lock(lock)
    }

    @Override
    Query order(Order order) {
        detachedCriteria.order(order)
        return this
    }

    @Override
    Query clearOrders() {
        detachedCriteria.orders.clear()
        super.clearOrders()
        return this
    }

    @Override
    Query join(String property) {
        fetchJoinPaths.add(property)
        detachedCriteria.join(property)
        return this
    }

    @Override
    Query join(String property, JoinType joinType) {
        fetchJoinPaths.add(property)
        detachedCriteria.join(property, joinType)
        return this
    }

    /**
     * The association paths requested as eager join fetches via {@link #join(String)} - for
     * example by a dynamic finder invoked with {@code [fetch: [assoc: 'join']]}. These are
     * materialized as JPA {@code root.fetch(...)} joins so the associations are eagerly
     * initialized, matching the Hibernate 5 behaviour.
     */
    Set<String> getFetchJoinPaths() {
        return Collections.unmodifiableSet(fetchJoinPaths)
    }

    @Override
    Query select(String property) {
        detachedCriteria.select(property)
        // Ensure property is added to projections for Hibernate 7
        projections.property(property)
        return this
    }

    @Override
    List list() {
        firePreQueryEvent()
        List results = executeList()
        return firePostQueryEvent(results)
    }

    private List executeList() {
        return hibernateQueryExecutor.list(currentSession, jpaCriteriaQuery)
    }

    List list(Session session) {
        return hibernateQueryExecutor.list(session, jpaCriteriaQuery)
    }

    private HibernateQueryExecutor getHibernateQueryExecutor() {
        return new HibernateQueryExecutor(
                offset, max, lockResult, queryCache, fetchSize, timeout, flushMode, readOnly, proxyHandler)
    }

    JpaCriteriaQuery<?> getJpaCriteriaQuery() {
        ConversionService conversionService = session.mappingContext.conversionService
        return new JpaCriteriaQueryCreator(
                        projections, criteriaBuilder, (GrailsHibernatePersistentEntity) this.@entity, detachedCriteria, conversionService, this)
                .createQuery()
    }

    void setFetchSize(Integer fetchSize) {
        this.fetchSize = fetchSize
    }

    @Override
    protected void flushBeforeQuery() {
        // do nothing
    }

    @Override
    Object singleResult() {
        firePreQueryEvent()
        Object result = executeSingleResult()
        return firePostQueryEvent(result)
    }

    private Object executeSingleResult() {
        return hibernateQueryExecutor.singleResult(currentSession, jpaCriteriaQuery)
    }

    Object singleResult(Session session) {
        return hibernateQueryExecutor.singleResult(session, jpaCriteriaQuery)
    }

    @Override
    Number countResults() {
        firePreQueryEvent()

        Number result
        if (projections.projectionList.isEmpty()) {
            projections().count()
            result = (Number) executeSingleResult()
        } else {
            HibernateCriteriaBuilder cb = criteriaBuilder

            JpaCriteriaQuery<Long> countQuery = cb.createQuery(Long)
            JpaSubQuery<Tuple> innerSubquery = countQuery.subquery(Tuple)

            ConversionService cs = session.mappingContext.conversionService
            new JpaCriteriaQueryCreator(projections, cb, (GrailsHibernatePersistentEntity) this.@entity, detachedCriteria, cs).populateSubquery(innerSubquery)

            countQuery.from(innerSubquery)
            countQuery.select(cb.count(cb.literal(1)))
            result = (Number) hibernateQueryExecutor.singleResult(currentSession, countQuery)
        }

        return (Number) firePostQueryEvent(result)
    }

    private void firePreQueryEvent() {
        Datastore datastore = session.datastore
        ApplicationEventPublisher publisher = datastore.applicationEventPublisher
        if (publisher != null) {
            publisher.publishEvent(new PreQueryEvent(datastore, this))
        }
    }

    private List firePostQueryEvent(List results) {
        Datastore datastore = session.datastore
        ApplicationEventPublisher publisher = datastore.applicationEventPublisher
        if (publisher != null) {
            PostQueryEvent postQueryEvent = new PostQueryEvent(datastore, this, results)
            publisher.publishEvent(postQueryEvent)
            return postQueryEvent.results
        }
        return results
    }

    private Object firePostQueryEvent(Object result) {
        List<?> results = firePostQueryEvent(Collections.singletonList(result))
        return results.isEmpty() ? null : results.get(0)
    }

    Object scroll() {
        firePreQueryEvent()
        return hibernateQueryExecutor.scroll(currentSession, jpaCriteriaQuery)
    }

    Object scroll(Session session) {
        return hibernateQueryExecutor.scroll(session, jpaCriteriaQuery)
    }

    private Session getCurrentSession() {
        return sessionFactory.currentSession
    }

    private SessionFactory getSessionFactory() {
        return ((IHibernateTemplate) session.nativeInterface).sessionFactory
    }

    HibernateCriteriaBuilder getCriteriaBuilder() {
        return sessionFactory.criteriaBuilder
    }

    @Override
    protected List executeQuery(PersistentEntity entity, Junction criteria) {
        return list()
    }

    protected String calculatePropertyName(String property) {
        if (alias == null) {
            return property
        }
        return "${alias}.${property}".toString()
    }

    protected String generateAlias(String associationName) {
        return "${calculatePropertyName(associationName)}${calculatePropertyName(ALIAS)}${aliasCount++}".toString()
    }

    Query in(String propertyName, QueryableCriteria<?> subquery) {
        detachedCriteria.inList(calculatePropertyName(propertyName), subquery)
        return this
    }

    void setTimeout(Integer timeout) {
        this.timeout = timeout
    }

    void setHibernateFlushMode(FlushMode flushMode) {
        this.flushMode = GrailsQueryFlushMode.mapToHibernateQueryFlushMode(flushMode)
    }

    void setReadOnly(Boolean readOnly) {
        this.readOnly = readOnly
    }

    DetachedCriteria<?> getHibernateCriteria() {
        return detachedCriteria
    }

    Query notIn(String propertyName, QueryableCriteria<?> subquery) {
        detachedCriteria.notIn(calculatePropertyName(propertyName), subquery)
        return this
    }

    Query exists(QueryableCriteria<?> subquery) {
        detachedCriteria.exists(subquery)
        return this
    }

    Query notExits(QueryableCriteria<?> subquery) {
        detachedCriteria.notExists(subquery)
        return this
    }

    Query gtAll(String propertyName, QueryableCriteria<?> subquery) {
        detachedCriteria.gtAll(calculatePropertyName(propertyName), subquery)
        return this
    }

    Query geAll(String propertyName, QueryableCriteria<?> subquery) {
        detachedCriteria.geAll(calculatePropertyName(propertyName), subquery)
        return this
    }

    Query ltAll(String propertyName, QueryableCriteria<?> subquery) {
        detachedCriteria.ltAll(calculatePropertyName(propertyName), subquery)
        return this
    }

    Query leAll(String propertyName, QueryableCriteria<?> subquery) {
        detachedCriteria.leAll(calculatePropertyName(propertyName), subquery)
        return this
    }

    Query gtSome(String propertyName, QueryableCriteria<?> subquery) {
        detachedCriteria.gtSome(calculatePropertyName(propertyName), subquery)
        return this
    }

    Query geSome(String propertyName, QueryableCriteria<?> subquery) {
        detachedCriteria.geSome(calculatePropertyName(propertyName), subquery)
        return this
    }

    Query ltSome(String propertyName, QueryableCriteria<?> subquery) {
        detachedCriteria.ltSome(calculatePropertyName(propertyName), subquery)
        return this
    }

    Query leSome(String propertyName, QueryableCriteria<?> subquery) {
        detachedCriteria.leSome(calculatePropertyName(propertyName), subquery)
        return this
    }

    Query eqAll(String propertyName, QueryableCriteria propertyValue) {
        detachedCriteria.eqAll(calculatePropertyName(propertyName), propertyValue)
        return this
    }

    Query ne(String propertyName, Object propertyValue) {
        detachedCriteria.ne(calculatePropertyName(propertyName), propertyValue)
        return this
    }

    Query eqProperty(String propertyName, String otherPropertyName) {
        detachedCriteria.eqProperty(calculatePropertyName(propertyName), otherPropertyName)
        return this
    }

    Query neProperty(String propertyName, String otherPropertyName) {
        detachedCriteria.neProperty(calculatePropertyName(propertyName), otherPropertyName)
        return this
    }

    Query gtProperty(String propertyName, String otherPropertyName) {
        detachedCriteria.gtProperty(calculatePropertyName(propertyName), otherPropertyName)
        return this
    }

    Query geProperty(String propertyName, String otherPropertyName) {
        detachedCriteria.geProperty(calculatePropertyName(propertyName), otherPropertyName)
        return this
    }

    Query ltProperty(String propertyName, String otherPropertyName) {
        detachedCriteria.ltProperty(calculatePropertyName(propertyName), otherPropertyName)
        return this
    }

    Query leProperty(String propertyName, String otherPropertyName) {
        detachedCriteria.leProperty(calculatePropertyName(propertyName), otherPropertyName)
        return this
    }

    Query sizeEq(String propertyName, int size) {
        detachedCriteria.sizeEq(calculatePropertyName(propertyName), size)
        return this
    }

    Query sizeGt(String propertyName, int size) {
        detachedCriteria.sizeGt(calculatePropertyName(propertyName), size)
        return this
    }

    Query sizeGe(String propertyName, int size) {
        detachedCriteria.sizeGe(calculatePropertyName(propertyName), size)
        return this
    }

    Query sizeLe(String propertyName, int size) {
        detachedCriteria.sizeLe(calculatePropertyName(propertyName), size)
        return this
    }

    Query sizeLt(String propertyName, int size) {
        detachedCriteria.sizeLt(calculatePropertyName(propertyName), size)
        return this
    }

    @Override
    ProjectionList projections() {
        return jpaProjectionList
    }

    protected class JpaProjectionList extends ProjectionList {

        @Override
        ProjectionList add(Projection p) {
            super.add(p)
            return this
        }

        @Override
        org.grails.datastore.mapping.query.api.ProjectionList countDistinct(String property) {
            add(Projections.countDistinct(property))
            return this
        }

        @Override
        org.grails.datastore.mapping.query.api.ProjectionList distinct(String property) {
            add(Projections.distinct(property))
            return this
        }

        @Override
        org.grails.datastore.mapping.query.api.ProjectionList rowCount() {
            return count()
        }

        @Override
        ProjectionList id() {
            add(Projections.id())
            return this
        }

        @Override
        ProjectionList count() {
            add(Projections.count())
            return this
        }

        @Override
        ProjectionList property(String name) {
            add(Projections.property(name))
            return this
        }

        @Override
        ProjectionList sum(String name) {
            add(Projections.sum(name))
            return this
        }

        @Override
        ProjectionList min(String name) {
            add(Projections.min(name))
            return this
        }

        @Override
        ProjectionList max(String name) {
            add(Projections.max(name))
            return this
        }

        @Override
        ProjectionList avg(String name) {
            add(Projections.avg(name))
            return this
        }

        @Override
        ProjectionList distinct() {
            add(Projections.distinct())
            return this
        }

    }

    Query sizeNe(String propertyName, int size) {
        detachedCriteria.sizeNe(calculatePropertyName(propertyName), size)
        return this
    }

    @Override
    Query maxResults(int maxResults) {
        this.max = maxResults
        return this
    }

    Query distinct() {
        projections.add(Projections.distinct())
        return this
    }

    @Override
    @SuppressWarnings([
        'PMD.CloneThrowsCloneNotSupportedException',
        'CloneDoesntCallSuperClone' // intentional: constructs a fresh instance via the session template
        // to avoid shallow-copying the live Session and DetachedCriteria state
    ])
    HibernateQuery clone() {
        HibernateSession hibernateSession = (HibernateSession) session
        GrailsHibernateTemplate hibernateTemplate =
                (GrailsHibernateTemplate) hibernateSession.nativeInterface
        return (HibernateQuery) hibernateTemplate.execute({ Session session ->
            HibernateQuery hibernateQuery = new HibernateQuery(hibernateSession, (GrailsHibernatePersistentEntity) this.@entity)
            if (this.max != null && this.max > 0) {
                hibernateQuery.max(this.max)
            }
            if (this.offset != null && this.offset > 0) {
                hibernateQuery.offset(this.offset)
            }
            hibernateQuery.detachedCriteria = this.detachedCriteria.clone()

            return hibernateQuery
        })
    }

}
