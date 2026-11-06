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

package org.grails.datastore.gorm.query.criteria

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode

import jakarta.persistence.FetchType
import jakarta.persistence.criteria.JoinType

import org.grails.datastore.gorm.finders.FinderMethod
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.query.Projections
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.Restrictions
import org.grails.datastore.mapping.query.api.Criteria
import org.grails.datastore.mapping.query.api.ProjectionList
import org.grails.datastore.mapping.query.api.QueryableCriteria

/**
 * Abstract super class for DetachedCriteria implementations.
 *
 * <p>A criteria built with {@code buildLazy} holds a deferred query. The invariant: <strong>the deferred query runs
 * once, under a lock, before anything reads, changes, copies or derives from the criteria, and what it added is part
 * of the criteria from then on.</strong> Every method that touches state therefore calls
 * {@link #applyLazyCriteria()} first: the methods that change state, the getters, and {@link #clone()}, which the
 * clone-and-modify methods ({@code max}, {@code offset}, {@code withConnection}, {@code buildLazy}) use. A query reads
 * the criteria through {@link #materializeLazyCriteria()}, which returns a copy, so a translator that extends the
 * result never changes the criteria it was given. A method added to this class that reads or writes state must
 * follow the same rule; {@code DetachedCriteriaDeferredQuerySpec} lists the ways in and fails for one that does not.</p>
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
abstract class AbstractDetachedCriteria<T> implements Criteria, Cloneable {

    protected List<Query.Criterion> criteria = []
    protected List<Query.Order> orders = []
    protected List<Query.Projection> projections = []
    protected Class targetClass
    protected List<FinderMethod> dynamicFinders
    protected Integer defaultOffset
    protected Integer defaultMax

    protected List<Query.Junction> junctions = []
    protected PersistentEntity persistentEntity
    protected Map<String, FetchType> fetchStrategies = [:]
    protected Map<String, JoinType> joinTypes = [:]
    protected volatile Closure lazyQuery

    /** Set once the deferred query has run, so that a query reads this criteria through a copy; see {@link #materializeLazyCriteria()}. */
    private volatile boolean lazyApplied

    /** The thread that is running the deferred query, so that the calls the query makes do not run it again. */
    private volatile Thread lazyApplyingThread
    protected String alias
    protected String connectionName = ConnectionSource.DEFAULT
    protected Map<String, DetachedAssociationCriteria> associationCriteriaMap = [:]

    ProjectionList projectionList = new DetachedProjections(projections)

    /**
     * Constructs a DetachedCriteria instance target the given class and alias for the name
     * @param targetClass The target class
     * @param alias The root alias to be used in queries
     */
    AbstractDetachedCriteria(Class<T> targetClass, String alias = null) {
        this.targetClass = targetClass
        this.alias = alias
    }

    /**
     * @return Obtain the fetch strategies for each property
     */
    Map<String, FetchType> getFetchStrategies() {
        applyLazyCriteria()
        return Collections.unmodifiableMap(fetchStrategies)
    }

    /**
     * @return Obtain the join types
     */
    Map<String, JoinType> getJoinTypes() {
        applyLazyCriteria()
        return Collections.unmodifiableMap(joinTypes)
    }
    /**
     * @return The root alias to be used for the query
     */
    String getAlias() {
        applyLazyCriteria()
        return this.alias
    }

    /**
     * Sets the root alias to be used for the query
     * @param alias The alias
     * @return The alias
     */
    Criteria setAlias(String alias) {
        applyLazyCriteria()
        this.alias = alias
        return this
    }

    /**
     * If the underlying datastore supports aliases, then an alias is created for the given association
     *
     * @param associationPath The name of the association
     * @param alias The alias
     * @return This create
     */
    Criteria createAlias(String associationPath, String alias) {
        applyLazyCriteria()
        initialiseIfNecessary(targetClass)
        PersistentProperty prop
        if (associationPath.contains('.')) {
            def tokens = associationPath.split(/\./)
            def entity = this.persistentEntity
            for (t in tokens) {
                prop = entity.getPropertyByName(t)
                if (!(prop instanceof Association)) {
                    throw new IllegalArgumentException("Argument [$associationPath] is not an association")
                }
                else {
                    entity = ((Association) prop).associatedEntity
                }
            }
        }
        else {
            prop = persistentEntity.getPropertyByName(associationPath)
        }
        if (!(prop instanceof Association)) {
            throw new IllegalArgumentException("Argument [$associationPath] is not an association")
        }

        Association a = (Association) prop
        DetachedAssociationCriteria associationCriteria = associationCriteriaMap[associationPath]
        if (associationCriteria == null) {
            associationCriteria = new DetachedAssociationCriteria(a.associatedEntity.javaClass, a, associationPath, alias)
            associationCriteriaMap[associationPath] = associationCriteria
            add(associationCriteria)
        }
        else {
            associationCriteria.setAlias(alias)
        }
        return associationCriteria
    }

    /**
     * Specifies whether a join query should be used (if join queries are supported by the underlying datastore)
     *
     * @param property The property
     * @return The query
     */
    Criteria join(String property) {
        applyLazyCriteria()
        fetchStrategies[property] = FetchType.EAGER
        return this
    }

    /**
     * Specifies whether a join query should be used (if join queries are supported by the underlying datastore)
     *
     * @param property The property
     * @param joinType The join type
     * @return The query
     */
    Criteria join(String property, JoinType joinType) {
        applyLazyCriteria()
        fetchStrategies[property] = FetchType.EAGER
        joinTypes[property] = joinType
        return this
    }

    /**
     * Specifies whether a select (lazy) query should be used (if join queries are supported by the underlying datastore)
     *
     * @param property The property
     * @return The query
     */
    Criteria select(String property) {
        applyLazyCriteria()
        fetchStrategies[property] = FetchType.LAZY
        return this
    }

    T getPersistentClass() {
        (T) getPersistentEntity().getJavaClass()
    }

    PersistentEntity getPersistentEntity() {
        if (persistentEntity == null) initialiseIfNecessary(targetClass)
        return persistentEntity
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    protected initialiseIfNecessary(Class<T> targetClass) {
        if (dynamicFinders != null) {
            return
        }

        try {
            dynamicFinders = targetClass.gormDynamicFinders
            persistentEntity = targetClass.gormPersistentEntity
        } catch (MissingPropertyException mpe) {
            throw new IllegalArgumentException("Class [$targetClass.name] is not a domain class")
        }
    }

    void add(Query.Criterion criterion) {
        applyLazyCriteria()
        if (criterion instanceof Query.PropertyCriterion) {
            if (criterion.value instanceof Closure) {
                criterion.value = buildQueryableCriteria((Closure) criterion.value)
            }
        }
        if (junctions)  {
            junctions[-1].add(criterion)
        }
        else {
            criteria << criterion
        }
    }

    List<Query.Criterion> getCriteria() {
        applyLazyCriteria()
        criteria
    }

    List<Query.Projection> getProjections() {
        applyLazyCriteria()
        projections
    }

    List<Query.Order> getOrders() {
        applyLazyCriteria()
        orders
    }

    /**
     * Evaluate projections within the context of the given closure
     *
     * @param callable The callable
     * @return  The projection list
     */
    Criteria projections(@DelegatesTo(value = ProjectionList, strategy = Closure.DELEGATE_FIRST) Closure callable) {
        callable.delegate = projectionList
        callable.resolveStrategy = Closure.DELEGATE_FIRST
        callable.call()
        return this
    }

    /**
     * Handles a conjunction
     * @param callable Callable closure
     * @return This criterion
     */
    Criteria and(@DelegatesTo(value = AbstractDetachedCriteria, strategy = Closure.DELEGATE_FIRST) Closure callable) {
        junctions << new Query.Conjunction()
        handleJunction((Closure) callable)
        return this
    }

    /**
     * Handles a disjunction
     * @param callable Callable closure
     * @return This criterion
     */
    Criteria or(@DelegatesTo(value = AbstractDetachedCriteria, strategy = Closure.DELEGATE_FIRST) Closure callable) {
        junctions << new Query.Disjunction()
        handleJunction((Closure) callable)
        return this
    }

    /**
     * Handles a disjunction
     * @param callable Callable closure
     * @return This criterion
     */
    Criteria not(@DelegatesTo(value = AbstractDetachedCriteria, strategy = Closure.DELEGATE_FIRST) Closure callable) {
        junctions << new Query.Negation()
        handleJunction((Closure) callable)
        return this
    }

    /**
     * @see Criteria
     */
    Criteria 'in'(String propertyName, Collection values) {
        inList(propertyName, values)
    }

    /**
     * @see Criteria
     */
    Criteria 'in'(String propertyName, QueryableCriteria subquery) {
        inList(propertyName, subquery)
    }

    @Override
    Criteria inList(String propertyName, QueryableCriteria<?> subquery) {
        add(Restrictions.in(propertyName, subquery))
        return this
    }

    @Override
    Criteria 'in'(String propertyName, @DelegatesTo(value = AbstractDetachedCriteria, strategy = Closure.DELEGATE_FIRST) Closure<?> subquery) {
        inList(propertyName, buildQueryableCriteria((Closure) subquery))
    }

    @Override
    Criteria inList(String propertyName, @DelegatesTo(value = AbstractDetachedCriteria, strategy = Closure.DELEGATE_FIRST) Closure<?> subquery) {
        inList(propertyName, buildQueryableCriteria((Closure) subquery))
    }

    /**
     * @see Criteria
     */
    Criteria 'in'(String propertyName, Object[] values) {
        inList(propertyName, values)
    }

    @Override
    Criteria notIn(String propertyName, QueryableCriteria<?> subquery) {
        add(Restrictions.notIn(propertyName, subquery))
        return this
    }

    @Override
    Criteria notIn(String propertyName, @DelegatesTo(value = AbstractDetachedCriteria, strategy = Closure.DELEGATE_FIRST) Closure<?> subquery) {
        notIn(propertyName, buildQueryableCriteria((Closure) subquery))
    }

    /**
     * @see Criteria
     */
    Criteria order(String propertyName) {
        applyLazyCriteria()
        orders << new Query.Order(propertyName)
        return this
    }

    @Override
    Criteria order(Query.Order o) {
        applyLazyCriteria()
        orders << o
        return this
    }

    /**
     * @see Criteria
     */
    Criteria order(String propertyName, String direction) {
        applyLazyCriteria()
        orders << new Query.Order(propertyName, Query.Order.Direction.valueOf(direction.toUpperCase()))
        return this
    }

    /**
     * @see Criteria
     */
    Criteria inList(String propertyName, Collection values) {
        add(Restrictions.in(propertyName, convertArgumentList(values)))
        return this
    }

    protected List convertArgumentList(Collection argList) {
        List convertedList = new ArrayList(argList.size())
        for (Object item : argList) {
            if (item instanceof CharSequence) {
                item = item.toString()
            }
            convertedList.add(item)
        }
        return convertedList
    }
    /**
     * @see Criteria
     */
    Criteria inList(String propertyName, Object[] values) {
        add(Restrictions.in(propertyName, convertArgumentList(Arrays.asList(values))))
        return this
    }

    /**
     * @see Criteria
     */
    Criteria sizeEq(String propertyName, int size) {
        add(Restrictions.sizeEq(propertyName, size))
        return this
    }

    /**
     * @see Criteria
     */
    Criteria sizeGt(String propertyName, int size) {
        add(Restrictions.sizeGt(propertyName, size))
        return this
    }

    /**
     * @see Criteria
     */
    Criteria sizeGe(String propertyName, int size) {
        add(Restrictions.sizeGe(propertyName, size))
        return this
    }

    /**
     * @see Criteria
     */
    Criteria sizeLe(String propertyName, int size) {
        add(Restrictions.sizeLe(propertyName, size))
        return this
    }

    /**
     * @see Criteria
     */
    Criteria sizeLt(String propertyName, int size) {
        add(Restrictions.sizeLt(propertyName, size))
        return this
    }

    /**
     * @see Criteria
     */
    Criteria sizeNe(String propertyName, int size) {
        add(Restrictions.sizeNe(propertyName, size))
        return this
    }

    /**
     * @see Criteria
     */
    Criteria eqProperty(String propertyName, String otherPropertyName) {
        add(Restrictions.eqProperty(propertyName, otherPropertyName))
        return this
    }

    /**
     * @see Criteria#neProperty(java.lang.String, java.lang.String)
     */
    Criteria neProperty(String propertyName, String otherPropertyName) {
        add(Restrictions.neProperty(propertyName, otherPropertyName))
        return this
    }

    /**
     * @see Criteria#allEq(java.util.Map)
     */
    @Override
    Criteria allEq(Map<String, Object> propertyValues) {
        Query.Conjunction conjunction = new Query.Conjunction()
        for (property in propertyValues.keySet()) {
            conjunction.add(Restrictions.eq(property, propertyValues.get(property)))
        }
        add(conjunction)
        return this
    }

    /**
     * @see Criteria
     */
    Criteria gtProperty(String propertyName, String otherPropertyName) {
        add(Restrictions.gtProperty(propertyName, otherPropertyName))
        return this
    }

    /**
     * @see Criteria
     */
    Criteria geProperty(String propertyName, String otherPropertyName) {
        add(Restrictions.geProperty(propertyName, otherPropertyName))
        return this
    }

    /**
     * @see Criteria
     */
    Criteria ltProperty(String propertyName, String otherPropertyName) {
        add(Restrictions.ltProperty(propertyName, otherPropertyName))
        return this
    }

    /**
     * @see Criteria
     */
    Criteria leProperty(String propertyName, String otherPropertyName) {
        add(Restrictions.leProperty(propertyName, otherPropertyName))
        return this
    }

    /**
     * @see Criteria
     */
    Criteria idEquals(Object value) {
        add(Restrictions.idEq(value))
        return this
    }

    /**
     * @see Criteria#exists(org.grails.datastore.mapping.query.api.QueryableCriteria)
     */
    @Override
    Criteria exists(QueryableCriteria<?> subquery) {
        add(new Query.Exists(subquery))
        return this
    }

    /**
     * @see Criteria#notExists(org.grails.datastore.mapping.query.api.QueryableCriteria)
     */
    @Override
    Criteria notExists(QueryableCriteria<?> subquery) {
        add(new Query.NotExists(subquery))
        return this
    }

    /**
     * @see Criteria
     */
    Criteria isEmpty(String propertyName) {
        add(Restrictions.isEmpty(propertyName))
        return this
    }

    /**
     * @see Criteria
     */
    Criteria isNotEmpty(String propertyName) {
        add(Restrictions.isNotEmpty(propertyName))
        return this
    }

    /**
     * @see Criteria
     */
    Criteria isNull(String propertyName) {
        add(Restrictions.isNull(propertyName))
        return this
    }

    /**
     * @see Criteria
     */
    Criteria isNotNull(String propertyName) {
        add(Restrictions.isNotNull(propertyName))
        return this
    }

    /**
     * @see Criteria
     */
    Criteria eq(String propertyName, Object propertyValue) {
        add(Restrictions.eq(propertyName, propertyValue))
        return this
    }

    /**
     * @see Criteria
     */
    Criteria idEq(Object propertyValue) {
        add(Restrictions.idEq(propertyValue))
        return this
    }

    /**
     * @see Criteria
     */
    Criteria ne(String propertyName, Object propertyValue) {
        add(Restrictions.ne(propertyName, propertyValue))
        return this
    }

    /**
     * @see Criteria
     */
    Criteria between(String propertyName, Object start, Object finish) {
        add(Restrictions.between(propertyName, start, finish))
        return this
    }

    /**
     * @see Criteria
     */
    Criteria gte(String property, Object value) {
        add(Restrictions.gte(property, value))
        return this
    }

    /**
     * @see Criteria
     */
    Criteria ge(String property, Object value) {
        gte(property, value)
    }

    /**
     * @see Criteria
     */
    Criteria gt(String property, Object value) {
        add(Restrictions.gt(property, value))
        return this
    }

    /**
     * @see Criteria
     */
    Criteria lte(String property, Object value) {
        add(Restrictions.lte(property, value))
        return this
    }

    /**
     * @see Criteria
     */
    Criteria le(String property, Object value) {
        lte(property, value)
    }

    /**
     * @see Criteria
     */
    Criteria lt(String property, Object value) {
        add(Restrictions.lt(property, value))
        return this
    }

    /**
     * @see Criteria
     */
    Criteria like(String propertyName, Object propertyValue) {
        add(Restrictions.like(propertyName, propertyValue.toString()))
        return this
    }

    /**
     * @see Criteria
     */
    Criteria ilike(String propertyName, Object propertyValue) {
        add(Restrictions.ilike(propertyName, propertyValue.toString()))
        return this
    }

    /**
     * @see Criteria
     */
    Criteria rlike(String propertyName, Object propertyValue) {
        add(Restrictions.rlike(propertyName, propertyValue.toString()))
        return this
    }

    Criteria eqAll(String propertyName, @DelegatesTo(value = AbstractDetachedCriteria, strategy = Closure.DELEGATE_FIRST) Closure<?> propertyValue) {
        eqAll(propertyName, buildQueryableCriteria((Closure) propertyValue))
    }

    Criteria gtAll(String propertyName, @DelegatesTo(value = AbstractDetachedCriteria, strategy = Closure.DELEGATE_FIRST) Closure<?> propertyValue) {
        gtAll(propertyName, buildQueryableCriteria((Closure) propertyValue))
    }

    Criteria ltAll(String propertyName, @DelegatesTo(value = AbstractDetachedCriteria, strategy = Closure.DELEGATE_FIRST) Closure<?> propertyValue) {
        ltAll(propertyName, buildQueryableCriteria((Closure) propertyValue))
    }

    Criteria geAll(String propertyName, @DelegatesTo(value = AbstractDetachedCriteria, strategy = Closure.DELEGATE_FIRST) Closure<?> propertyValue) {
        geAll(propertyName, buildQueryableCriteria((Closure) propertyValue))
    }

    Criteria leAll(String propertyName, @DelegatesTo(value = AbstractDetachedCriteria, strategy = Closure.DELEGATE_FIRST) Closure<?> propertyValue) {
        leAll(propertyName, buildQueryableCriteria((Closure) propertyValue))
    }

    @Override
    Criteria eqAll(String propertyName, QueryableCriteria propertyValue) {
        add(new Query.EqualsAll(propertyName, propertyValue))
        return this
    }

    @Override
    Criteria gtAll(String propertyName, QueryableCriteria propertyValue) {
        add(new Query.GreaterThanAll(propertyName, propertyValue))
        return this
    }

    @Override
    Criteria gtSome(String propertyName, QueryableCriteria propertyValue) {
        add(new Query.GreaterThanSome(propertyName, propertyValue))
        return this
    }

    @Override
    Criteria gtSome(String propertyName, @DelegatesTo(value = AbstractDetachedCriteria, strategy = Closure.DELEGATE_FIRST) Closure<?> propertyValue) {
        gtSome(propertyName, buildQueryableCriteria((Closure) propertyValue))
    }

    @Override
    Criteria geSome(String propertyName, QueryableCriteria propertyValue) {
        add(new Query.GreaterThanEqualsSome(propertyName, propertyValue))
        return this
    }

    @Override
    Criteria geSome(String propertyName, @DelegatesTo(value = AbstractDetachedCriteria, strategy = Closure.DELEGATE_FIRST) Closure<?> propertyValue) {
        geSome(propertyName, buildQueryableCriteria((Closure) propertyValue))
    }

    @Override
    Criteria ltSome(String propertyName, QueryableCriteria propertyValue) {
        add(new Query.LessThanSome(propertyName, propertyValue))
        return this
    }

    @Override
    Criteria ltSome(String propertyName, @DelegatesTo(value = AbstractDetachedCriteria, strategy = Closure.DELEGATE_FIRST) Closure<?> propertyValue) {
        ltSome(propertyName, buildQueryableCriteria((Closure) propertyValue))
    }

    @Override
    Criteria leSome(String propertyName, QueryableCriteria propertyValue) {
        add(new Query.LessThanEqualsSome(propertyName, propertyValue))
        return this
    }

    @Override
    Criteria leSome(String propertyName, @DelegatesTo(value = AbstractDetachedCriteria, strategy = Closure.DELEGATE_FIRST) Closure<?> propertyValue) {
        leSome(propertyName, buildQueryableCriteria((Closure) propertyValue))
    }

    @Override
    Criteria ltAll(String propertyName, QueryableCriteria propertyValue) {
        add(new Query.LessThanAll(propertyName, propertyValue))
        return this
    }

    @Override
    Criteria geAll(String propertyName, QueryableCriteria propertyValue) {
        add(new Query.GreaterThanEqualsAll(propertyName, propertyValue))
        return this
    }

    @Override
    Criteria leAll(String propertyName, QueryableCriteria propertyValue) {
        add(new Query.LessThanEqualsAll(propertyName, propertyValue))
        return this
    }

    class DetachedProjections implements ProjectionList {

        List<Query.Projection> projections

        DetachedProjections(List<Query.Projection> projections) {
            this.projections = projections
        }

        ProjectionList avg(String name) {
            AbstractDetachedCriteria.this.applyLazyCriteria()
            projections << Projections.avg(name)
            return this
        }

        ProjectionList max(String name) {
            AbstractDetachedCriteria.this.applyLazyCriteria()
            projections << Projections.max(name)
            return this
        }

        ProjectionList min(String name) {
            AbstractDetachedCriteria.this.applyLazyCriteria()
            projections << Projections.min(name)
            return this
        }

        ProjectionList sum(String name) {
            AbstractDetachedCriteria.this.applyLazyCriteria()
            projections << Projections.sum(name)
            return this
        }

        ProjectionList property(String name) {
            AbstractDetachedCriteria.this.applyLazyCriteria()
            projections << Projections.property(name)
            return this
        }

        ProjectionList rowCount() {
            AbstractDetachedCriteria.this.applyLazyCriteria()
            projections << Projections.count()
            return this
        }

        ProjectionList distinct(String property) {
            AbstractDetachedCriteria.this.applyLazyCriteria()
            projections << Projections.distinct(property)
            return this
        }

        ProjectionList distinct() {
            AbstractDetachedCriteria.this.applyLazyCriteria()
            projections << Projections.distinct()
            return this
        }

        ProjectionList countDistinct(String property) {
            AbstractDetachedCriteria.this.applyLazyCriteria()
            projections << Projections.countDistinct(property)
            return this
        }

        @Override
        ProjectionList groupProperty(String property) {
            AbstractDetachedCriteria.this.applyLazyCriteria()
            projections << Projections.groupProperty(property)
            return this
        }

        ProjectionList count() {
            AbstractDetachedCriteria.this.applyLazyCriteria()
            projections << Projections.count()
            return this
        }

        ProjectionList id() {
            AbstractDetachedCriteria.this.applyLazyCriteria()
            projections << Projections.id()
            return this
        }
    }

    /**
     * Where method derives a new query from this query. This method will not mutate the original query, but instead return a new one.
     *
     * @param additionalQuery The additional query
     * @return A new query
     */
    AbstractDetachedCriteria<T> where(@DelegatesTo(value = AbstractDetachedCriteria, strategy = Closure.DELEGATE_FIRST) Closure additionalQuery) {
        AbstractDetachedCriteria<T> newQuery = clone()
        return newQuery.build(additionalQuery)
    }

    /**
     * Where method derives a new query from this query. This method will not mutate the original query, but instead return a new one.
     *
     * @param additionalQuery The additional query
     * @return A new query
     */
    AbstractDetachedCriteria<T> whereLazy(@DelegatesTo(value = AbstractDetachedCriteria, strategy = Closure.DELEGATE_FIRST) Closure additionalQuery) {
        AbstractDetachedCriteria<T> newQuery = clone()
        return newQuery.build(additionalQuery)
    }

    /**
     * Enable the builder syntax for constructing Criteria
     *
     * @param callable The callable closure
     * @return A new criteria instance
     */

    AbstractDetachedCriteria<T> build(@DelegatesTo(value = AbstractDetachedCriteria, strategy = Closure.DELEGATE_FIRST) Closure callable) {
        AbstractDetachedCriteria newCriteria = this.clone()
        final Closure clonedClosure = (Closure) callable.clone()
        clonedClosure.setResolveStrategy(Closure.DELEGATE_FIRST)
        newCriteria.with(clonedClosure)
        return newCriteria
    }

    /**
     * Enable the builder syntax for constructing Criteria, deferring the closure until the criteria is first used.
     * The closure runs once, on first use, and what it adds becomes part of this criteria: a later change to a
     * value it reads does not change the criteria, and a criteria extended afterwards, or derived from it, keeps
     * what the closure added. A query reads the criteria through a copy, so translating it never changes it, and
     * the closure runs under a lock, so threads that use the criteria for the first time at once see it run once.
     *
     * @param callable The callable closure
     * @return A new criteria instance
     */

    AbstractDetachedCriteria<T> buildLazy(@DelegatesTo(value = AbstractDetachedCriteria, strategy = Closure.DELEGATE_FIRST) Closure callable) {
        AbstractDetachedCriteria newCriteria = this.clone()
        newCriteria.lazyQuery = callable
        return newCriteria
    }

    /**
     * Create a return a new DetachedCriteria that uses the given connection
     *
     * @param name The connection name
     * @param callable The callable closure
     * @return A new criteria instance
     * @since 6.1
     */

    AbstractDetachedCriteria<T> withConnection(String name) {
        AbstractDetachedCriteria newCriteria = this.clone()
        newCriteria.connectionName = name
        return newCriteria
    }

    @Override
    @CompileStatic
    AbstractDetachedCriteria<T> clone() {
        // a copy carries the result of the deferred query, never the query: it runs once, here or earlier, and
        // waits for a run on another thread, so that a copy is never half built
        applyLazyCriteria()
        synchronized (this) {
            AbstractDetachedCriteria criteria = newInstance()
            Map<DetachedAssociationCriteria, DetachedAssociationCriteria> associationCopies = new IdentityHashMap<>()
            List<Query.Criterion> clonedCriteria = new ArrayList<>(this.criteria.size())
            for (Query.Criterion criterion : this.criteria) {
                clonedCriteria.add(copyCriterion(criterion, associationCopies))
            }
            criteria.@criteria = clonedCriteria
            criteria.@alias = this.alias
            final projections = new ArrayList(this.projections)
            criteria.@projections = projections
            criteria.projectionList = criteria.newProjectionList(projections)
            criteria.@orders = new ArrayList(this.orders)
            criteria.defaultMax = defaultMax
            criteria.defaultOffset = defaultOffset
            criteria.@fetchStrategies = new HashMap<>(this.fetchStrategies)
            criteria.@joinTypes = new HashMap<>(this.joinTypes)
            criteria.@junctions = new ArrayList(this.junctions)
            criteria.@connectionName = this.connectionName
            // a copy made by the call that is running the deferred query, such as sort or where inside it, must not
            // carry that query: it is running, and a copy that held it would run it again, without end
            criteria.@lazyQuery = lazyApplyingThread == Thread.currentThread() ? null : this.lazyQuery
            Map<String, DetachedAssociationCriteria> clonedAssociations = new LinkedHashMap<>()
            for (Map.Entry<String, DetachedAssociationCriteria> entry : this.associationCriteriaMap.entrySet()) {
                clonedAssociations.put(entry.key, copyAssociationCriteria(entry.value, associationCopies))
            }
            criteria.@associationCriteriaMap = clonedAssociations
            return criteria
        }
    }

    /**
     * Copies a criterion for a clone. An association node is copied once per clone, and a junction ({@code and},
     * {@code or}, {@code not}) is copied with its children through the same map, so the criteria tree and the
     * association map of the copy always refer to the same nodes. Any other criterion is immutable and is shared.
     */
    @CompileStatic
    private static Query.Criterion copyCriterion(Query.Criterion source,
            Map<DetachedAssociationCriteria, DetachedAssociationCriteria> copies) {
        if (source instanceof DetachedAssociationCriteria) {
            return copyAssociationCriteria((DetachedAssociationCriteria) source, copies)
        }
        if (source instanceof Query.Junction) {
            Query.Junction junction = (Query.Junction) source
            Query.Junction copy
            if (junction instanceof Query.Disjunction) {
                copy = new Query.Disjunction()
            }
            else if (junction instanceof Query.Negation) {
                copy = new Query.Negation()
            }
            else {
                copy = new Query.Conjunction()
            }
            for (Query.Criterion child : junction.criteria) {
                copy.add(copyCriterion(child, copies))
            }
            return copy
        }
        return source
    }

    /**
     * Copies an association node once per clone, so the criteria list and the association map of the copy
     * refer to the same node and a change to that node never reaches the original.
     */
    @CompileStatic
    private static DetachedAssociationCriteria copyAssociationCriteria(DetachedAssociationCriteria source,
            Map<DetachedAssociationCriteria, DetachedAssociationCriteria> copies) {
        DetachedAssociationCriteria copy = copies.get(source)
        if (copy == null) {
            copy = (DetachedAssociationCriteria) source.clone()
            copies.put(source, copy)
        }
        return copy
    }

    /**
     * The projection list over the given list, owned by this criteria: its methods run the deferred query of this
     * criteria first. A copy must build its own through this method, because an inner class belongs to the
     * criteria whose code creates it.
     */
    protected ProjectionList newProjectionList(List<Query.Projection> projections) {
        new DetachedProjections(projections)
    }

    protected abstract AbstractDetachedCriteria newInstance()

    /**
     * Sets the default max to use and returns a new criteria instance. This method does not mutate the original criteria!
     *
     * @param max The max to use
     * @return A new DetachedCriteria instance derived from this
     */
    AbstractDetachedCriteria<T> max(int max) {
        AbstractDetachedCriteria newCriteria = this.clone()
        newCriteria.defaultMax = max
        return newCriteria
    }

    /**
     * Sets the default offset to use and returns a new criteria instance. This method does not mutate the original criteria!
     *
     * @param offset The offset to use
     * @return A new DetachedCriteria instance derived from this
     */
    AbstractDetachedCriteria<T> offset(int offset) {
        AbstractDetachedCriteria newCriteria = this.clone()
        newCriteria.defaultOffset = offset
        return newCriteria
    }

    /**
     * Adds a sort order to this criteria instance
     *
     * @param property The property to sort by
     * @return This criteria instance
     */
    AbstractDetachedCriteria<T> sort(String property) {
        AbstractDetachedCriteria newCriteria = this.clone()
        newCriteria.orders.add(new Query.Order(property))
        return newCriteria
    }

    /**
     * Adds a sort order to this criteria instance
     *
     * @param property The property to sort by
     * @param direction The direction to sort by
     * @return This criteria instance
     */
    AbstractDetachedCriteria<T> sort(String property, String direction) {
        AbstractDetachedCriteria newCriteria = this.clone()
        newCriteria.orders.add(new Query.Order(property, 'desc'.equalsIgnoreCase(direction) ? Query.Order.Direction.DESC : Query.Order.Direction.ASC))
        return newCriteria
    }

    /**
     * Adds a property projection
     *
     * @param property The property to project
     * @return This criteria instance
     */
    AbstractDetachedCriteria<T> property(String property) {
        AbstractDetachedCriteria newCriteria = this.clone()
        newCriteria.projectionList.property(property)
        return newCriteria
    }

    /**
     * Adds an id projection
     *
     * @param property The property to project
     * @return This criteria instance
     */
    AbstractDetachedCriteria<T> id() {
        AbstractDetachedCriteria newCriteria = this.clone()
        newCriteria.projectionList.id()
        return newCriteria
    }

    /**
     * Adds a avg projection
     *
     * @param property The property to avg by
     * @return This criteria instance
     */
    AbstractDetachedCriteria<T> avg(String property) {
        AbstractDetachedCriteria newCriteria = this.clone()
        newCriteria.projectionList.avg(property)
        return newCriteria
    }

    /**
     * Adds a sum projection
     *
     * @param property The property to sum by
     * @return This criteria instance
     */
    AbstractDetachedCriteria<T> sum(String property) {
        AbstractDetachedCriteria newCriteria = this.clone()
        newCriteria.projectionList.sum(property)
        return newCriteria
    }

    /**
     * Adds a sum projection
     *
     * @param property The property to min by
     * @return This criteria instance
     */
    AbstractDetachedCriteria<T> min(String property) {
        AbstractDetachedCriteria newCriteria = this.clone()
        newCriteria.projectionList.min(property)
        return newCriteria
    }

    /**
     * Adds a min projection
     *
     * @param property The property to max by
     * @return This criteria instance
     */
    AbstractDetachedCriteria<T> max(String property) {
        AbstractDetachedCriteria newCriteria = this.clone()
        newCriteria.projectionList.max(property)
        return newCriteria
    }

    def propertyMissing(String name) {
        final entity = getPersistentEntity()
        final p = entity.getPropertyByName(name)
        if (p == null) {
            throw new MissingPropertyException(name, AbstractDetachedCriteria)
        }
        return property(name)
    }

    /**
     * Adds a distinct property projection
     *
     * @param property The property to obtain the distinct value for
     * @return This criteria instance
     */
    AbstractDetachedCriteria<T> distinct(String property) {
        AbstractDetachedCriteria newCriteria = this.clone()
        newCriteria.projectionList.distinct(property)
        return newCriteria
    }

    /**
     * Method missing handler that deals with the invocation of dynamic finders
     *
     * @param methodName The method name
     * @param args The arguments
     * @return The result of the method call
     */
    @CompileDynamic
    def methodMissing(String methodName, args) {
        initialiseIfNecessary(targetClass)
        def method = dynamicFinders.find { FinderMethod f -> f.isMethodMatch(methodName) }
        if (method != null) {
            return method.invoke(targetClass, methodName, materializeLazyCriteria(), args)
        }

        if (!args) {
            throw new MissingMethodException(methodName, AbstractDetachedCriteria, args)
        }

        final prop = persistentEntity.getPropertyByName(methodName)
        if (!(prop instanceof Association)) {
            throw new MissingMethodException(methodName, AbstractDetachedCriteria, args)
        }

        def alias = args[0] instanceof CharSequence ? args[0].toString() : null

        // Explicit null checks: Groovy truth on a DetachedCriteria invokes asBoolean(),
        // which executes the criteria as a query - a spurious query for a repeated
        // reference to a persistent association, and an outright failure for a repeated
        // reference to an embedded component (its class is not a queryable root entity).
        DetachedAssociationCriteria existing = associationCriteriaMap[methodName]
        if (alias == null && existing != null) {
            alias = existing.alias
        }
        DetachedAssociationCriteria associationCriteria = alias ? new DetachedAssociationCriteria(prop.associatedEntity.javaClass, prop, alias)
                : new DetachedAssociationCriteria(prop.associatedEntity.javaClass, prop)

        associationCriteriaMap[methodName] = associationCriteria
        add(associationCriteria)

        def lastArg = args[-1]
        if (lastArg instanceof Closure) {
            Closure callable = lastArg
            callable.resolveStrategy = Closure.DELEGATE_FIRST

            Closure parentCallable = callable
            while (parentCallable.delegate instanceof Closure) {
                parentCallable = (Closure) parentCallable.delegate
            }

            def previous = parentCallable.delegate

            try {
                parentCallable.delegate = associationCriteria
                callable.call()
            } finally {
                parentCallable.delegate = previous
            }
        }
    }

    protected void handleJunction(Closure callable) {
        try {
            callable.delegate = this
            callable.resolveStrategy = Closure.DELEGATE_FIRST
            callable.call()
        }
        finally {
            def lastJunction = junctions.remove(junctions.size() - 1)
            add(lastJunction)
        }
    }

    protected abstract QueryableCriteria buildQueryableCriteria(Closure queryClosure)

    /**
     * Runs the pending {@code buildLazy} closure on this criteria, once, and clears it. Used when this criteria is
     * extended, and by {@link #materializeLazyCriteria()}. The closure runs under a lock, and the pending query is
     * cleared only after it has run, so a thread that finds it pending waits for the result instead of reading
     * a criteria that is half built. The calls the closure makes on this criteria reach here again on the same
     * thread, and return.
     */
    protected void applyLazyCriteria() {
        if (lazyQuery == null || lazyApplyingThread == Thread.currentThread()) {
            return
        }
        synchronized (this) {
            Closure pending = lazyQuery
            if (pending == null) {
                return
            }
            lazyApplyingThread = Thread.currentThread()
            try {
                this.with(pending)
            }
            finally {
                lazyApplied = true
                lazyQuery = null
                lazyApplyingThread = null
            }
        }
    }

    /**
     * Returns the criteria a query reads. A criteria with a deferred query runs it once, on this criteria, and
     * every call returns a fresh copy of the result, so the query can extend or translate the copy without changing
     * this criteria, which stays as it is, including what was added to it after the first query. A criteria without
     * a deferred query returns itself.
     */
    AbstractDetachedCriteria<T> materializeLazyCriteria() {
        if (lazyQuery == null && !lazyApplied) {
            return this
        }
        applyLazyCriteria()
        return clone()
    }

    @Override
    Criteria cache(boolean shouldCache) {
        // no-op for now
        this
    }

    @Override
    Criteria readOnly(boolean readOnly) {
        // no-op for now
        this
    }
}
