/* Copyright (C) 2011 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package grails.gorm

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.GormStaticApi
import org.grails.datastore.gorm.finders.DynamicFinder
import org.grails.datastore.gorm.query.GormOperations
import org.grails.datastore.gorm.query.criteria.AbstractDetachedCriteria
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.api.Criteria
import org.grails.datastore.mapping.query.api.ProjectionList
import org.grails.datastore.mapping.query.api.QueryAliasAwareSession
import org.grails.datastore.mapping.query.api.QueryArgumentsAware
import org.grails.datastore.mapping.query.api.QueryableCriteria

import jakarta.persistence.criteria.JoinType

/**
 * Represents criteria that is not bound to the current connection and can be built up and re-used at a later date.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class DetachedCriteria<T> extends AbstractDetachedCriteria<T> implements GormOperations<T>, QueryableCriteria<T>, Iterable<T> {

    /**
     * Constructs a DetachedCriteria instance target the given class and alias for the name
     * @param targetClass The target class
     * @param alias The root alias to be used in queries
     */
    DetachedCriteria(Class<T> targetClass, String alias = null) {
        super(targetClass, alias)
    }


    /**
     * Where method derives a new query from this query. This method will not mutate the original query, but instead return a new one.
     *
     * @param additionalQuery The additional query
     * @return A new query
     */
    @Override
    DetachedCriteria<T> where(@DelegatesTo(DetachedCriteria) Closure additionalQuery) {
        DetachedCriteria<T> newQuery = clone()
        return newQuery.build(additionalQuery)
    }

    @Override
    DetachedCriteria<T> withConnection(String name) {
        return (DetachedCriteria<T>)super.withConnection(name)
    }
/**
     * Where method derives a new query from this query. This method will not mutate the original query, but instead return a new one.
     *
     * @param additionalQuery The additional query
     * @return A new query
     */
    @Override
    DetachedCriteria<T> whereLazy(@DelegatesTo(DetachedCriteria) Closure additionalQuery) {
        DetachedCriteria<T> newQuery = clone()
        return newQuery.build(additionalQuery)
    }

    /**
     * Synonym for #get
     */
    T find(Map args = Collections.emptyMap(), @DelegatesTo(DetachedCriteria) Closure additionalCriteria = null) {
        get(args, additionalCriteria)
    }

    /**
     * Defines projections.
     *
     * @param callable The callable
     * @return This detached criteria
     */
    DetachedCriteria<T> projections(@DelegatesTo(ProjectionList) Closure callable) {
        callable.delegate = projectionList
        callable.resolveStrategy = Closure.DELEGATE_FIRST
        callable.call()
        return this
    }

    /**
     * Synonym for #get
     */
    T find(@DelegatesTo(DetachedCriteria) Closure additionalCriteria) {
        get(Collections.emptyMap(), additionalCriteria)
    }

    /**
     * Returns a single result matching the criterion contained within this DetachedCriteria instance
     *
     * @return A single entity
     */
    T get(Map args = Collections.emptyMap(), @DelegatesTo(DetachedCriteria) Closure additionalCriteria = null) {
        (T)withPopulatedQuery(args, additionalCriteria) { Query query ->
            query.singleResult()
        }
    }

    /**
     * Returns a single result matching the criterion contained within this DetachedCriteria instance
     *
     * @return A single entity
     */
    T get(@DelegatesTo(DetachedCriteria) Closure additionalCriteria) {
        get(Collections.emptyMap(), additionalCriteria)
    }

    /**
     * Lists all records matching the criterion contained within this DetachedCriteria instance
     *
     * @return A list of matching instances
     */
    List<T> list(Map args = Collections.emptyMap(), @DelegatesTo(DetachedCriteria) Closure additionalCriteria = null) {
        (List)withPopulatedQuery(args, additionalCriteria) { Query query ->
            if (args?.max) {
                return new PagedResultList(query)
            }
            return query.list()
        }
    }

    /**
     * Lists all records matching the criterion contained within this DetachedCriteria instance
     *
     * @return A list of matching instances
     */
    List<T> list(@DelegatesTo(DetachedCriteria) Closure additionalCriteria) {
        list(Collections.emptyMap(), additionalCriteria)
    }

    @Override
    Iterator<T> iterator() {
        return list().iterator()
    }

    @Override
    DetachedCriteria<T> join(String property) {
        return (DetachedCriteria<T>)super.join(property)
    }

    @Override
    DetachedCriteria<T> join(String property, JoinType joinType) {
        return (DetachedCriteria<T>)super.join(property, joinType)
    }

    @Override
    DetachedCriteria<T> select(String property) {
        return (DetachedCriteria<T>)super.select(property)
    }

    @Override
    DetachedCriteria<T> and(@DelegatesTo(AbstractDetachedCriteria) Closure callable) {
        return (DetachedCriteria<T>)super.and(callable)
    }

    @Override
    DetachedCriteria<T> or(@DelegatesTo(AbstractDetachedCriteria) Closure callable) {
        return (DetachedCriteria<T>)super.or(callable)
    }

    @Override
    DetachedCriteria<T> not(@DelegatesTo(AbstractDetachedCriteria) Closure callable) {
        return (DetachedCriteria<T>)super.not(callable)
    }

    @Override
    DetachedCriteria<T> "in"(String propertyName, Collection values) {
        return (DetachedCriteria<T>)super.in(propertyName, values)
    }

    @Override
    DetachedCriteria<T> "in"(String propertyName, QueryableCriteria subquery) {
        return (DetachedCriteria<T>)super.in(propertyName, subquery)
    }

    @Override
    DetachedCriteria<T> inList(String propertyName, QueryableCriteria<?> subquery) {
        return (DetachedCriteria<T>)super.inList(propertyName, subquery)
    }

    @Override
    DetachedCriteria<T> "in"(String propertyName, @DelegatesTo(AbstractDetachedCriteria) Closure<?> subquery) {
        return (DetachedCriteria<T>)super.in(propertyName, subquery)
    }

    @Override
    DetachedCriteria<T> inList(String propertyName, @DelegatesTo(AbstractDetachedCriteria) Closure<?> subquery) {
        return (DetachedCriteria<T>)super.inList(propertyName, subquery)
    }

    @Override
    DetachedCriteria<T> "in"(String propertyName, Object[] values) {
        return (DetachedCriteria<T>)super.in(propertyName, values)
    }

    @Override
    DetachedCriteria<T> notIn(String propertyName, QueryableCriteria<?> subquery) {
        return (DetachedCriteria<T>)super.notIn(propertyName, subquery)
    }

    @Override
    DetachedCriteria<T> notIn(String propertyName, @DelegatesTo(AbstractDetachedCriteria) Closure<?> subquery) {
        return (DetachedCriteria<T>)super.notIn(propertyName, subquery)
    }

    @Override
    DetachedCriteria<T> order(String propertyName) {
        return (DetachedCriteria<T>)super.order(propertyName)
    }

    @Override
    DetachedCriteria<T> order(Query.Order o) {
        return (DetachedCriteria<T>)super.order(o)
    }

    @Override
    DetachedCriteria<T> order(String propertyName, String direction) {
        return (DetachedCriteria<T>)super.order(propertyName, direction)
    }

    @Override
    DetachedCriteria<T> inList(String propertyName, Collection values) {
        return (DetachedCriteria<T>)super.inList(propertyName, values)
    }

    @Override
    DetachedCriteria<T> inList(String propertyName, Object[] values) {
        return (DetachedCriteria<T>)super.inList(propertyName, values)
    }

    @Override
    DetachedCriteria<T> sizeEq(String propertyName, int size) {
        return (DetachedCriteria<T>)super.sizeEq(propertyName, size)
    }

    @Override
    DetachedCriteria<T> sizeGt(String propertyName, int size) {
        return (DetachedCriteria<T>)super.sizeGt(propertyName, size)
    }

    @Override
    DetachedCriteria<T> sizeGe(String propertyName, int size) {
        return (DetachedCriteria<T>)super.sizeGe(propertyName, size)
    }

    @Override
    DetachedCriteria<T> sizeLe(String propertyName, int size) {
        return (DetachedCriteria<T>)super.sizeLe(propertyName, size)
    }

    @Override
    DetachedCriteria<T> sizeLt(String propertyName, int size) {
        return (DetachedCriteria<T>)super.sizeLt(propertyName, size)
    }

    @Override
    DetachedCriteria<T> sizeNe(String propertyName, int size) {
        return (DetachedCriteria<T>)super.sizeNe(propertyName, size)
    }

    @Override
    DetachedCriteria<T> eqProperty(String propertyName, String otherPropertyName) {
        return (DetachedCriteria<T>)super.eqProperty(propertyName, otherPropertyName)
    }

    @Override
    DetachedCriteria<T> neProperty(String propertyName, String otherPropertyName) {
        return (DetachedCriteria<T>)super.neProperty(propertyName, otherPropertyName)
    }

    @Override
    DetachedCriteria<T> allEq(Map<String, Object> propertyValues) {
        return (DetachedCriteria<T>)super.allEq(propertyValues)
    }

    @Override
    DetachedCriteria<T> gtProperty(String propertyName, String otherPropertyName) {
        return (DetachedCriteria<T>)super.gtProperty(propertyName, otherPropertyName)
    }

    @Override
    DetachedCriteria<T> geProperty(String propertyName, String otherPropertyName) {
        return (DetachedCriteria<T>)super.geProperty(propertyName, otherPropertyName)
    }

    @Override
    DetachedCriteria<T> ltProperty(String propertyName, String otherPropertyName) {
        return (DetachedCriteria<T>)super.ltProperty(propertyName, otherPropertyName)
    }

    @Override
    DetachedCriteria<T> leProperty(String propertyName, String otherPropertyName) {
        return (DetachedCriteria<T>)super.leProperty(propertyName, otherPropertyName)
    }

    @Override
    DetachedCriteria<T> idEquals(Object value) {
        return (DetachedCriteria<T>)super.idEquals(value)
    }

    @Override
    DetachedCriteria<T> exists(QueryableCriteria<?> subquery) {
        return (DetachedCriteria<T>)super.exists(subquery)
    }

    @Override
    DetachedCriteria<T> notExists(QueryableCriteria<?> subquery) {
        return (DetachedCriteria<T>)super.notExists(subquery)
    }

    @Override
    DetachedCriteria<T> isEmpty(String propertyName) {
        return (DetachedCriteria<T>)super.isEmpty(propertyName)
    }

    @Override
    DetachedCriteria<T> isNotEmpty(String propertyName) {
        return (DetachedCriteria<T>)super.isNotEmpty(propertyName)
    }

    @Override
    DetachedCriteria<T> isNull(String propertyName) {
        return (DetachedCriteria<T>)super.isNull(propertyName)
    }

    @Override
    DetachedCriteria<T> isNotNull(String propertyName) {
        return (DetachedCriteria<T>)super.isNotNull(propertyName)
    }

    @Override
    DetachedCriteria<T> eq(String propertyName, Object propertyValue) {
        return (DetachedCriteria<T>)super.eq(propertyName, propertyValue)
    }

    @Override
    DetachedCriteria<T> idEq(Object propertyValue) {
        return (DetachedCriteria<T>)super.idEq(propertyValue)
    }

    @Override
    DetachedCriteria<T> ne(String propertyName, Object propertyValue) {
        return (DetachedCriteria<T>)super.ne(propertyName, propertyValue)
    }

    @Override
    DetachedCriteria<T> between(String propertyName, Object start, Object finish) {
        return (DetachedCriteria<T>)super.between(propertyName, start, finish)
    }

    @Override
    DetachedCriteria<T> gte(String property, Object value) {
        return (DetachedCriteria<T>)super.gte(property, value)
    }

    @Override
    DetachedCriteria<T> ge(String property, Object value) {
        return (DetachedCriteria<T>)super.ge(property, value)
    }

    @Override
    DetachedCriteria<T> gt(String property, Object value) {
        return (DetachedCriteria<T>)super.gt(property, value)
    }

    @Override
    DetachedCriteria<T> lte(String property, Object value) {
        return (DetachedCriteria<T>)super.lte(property, value)
    }

    @Override
    DetachedCriteria<T> le(String property, Object value) {
        return (DetachedCriteria<T>)super.le(property, value)
    }

    @Override
    DetachedCriteria<T> lt(String property, Object value) {
        return (DetachedCriteria<T>)super.lt(property, value)
    }

    @Override
    DetachedCriteria<T> like(String propertyName, Object propertyValue) {
        return (DetachedCriteria<T>)super.like(propertyName, propertyValue)
    }

    @Override
    DetachedCriteria<T> ilike(String propertyName, Object propertyValue) {
        return (DetachedCriteria<T>)super.ilike(propertyName, propertyValue)
    }

    @Override
    DetachedCriteria<T> rlike(String propertyName, Object propertyValue) {
        return (DetachedCriteria<T>)super.rlike(propertyName, propertyValue)
    }

    @Override
    DetachedCriteria<T> eqAll(String propertyName, @DelegatesTo(AbstractDetachedCriteria) Closure<?> propertyValue) {
        return (DetachedCriteria<T>)super.eqAll(propertyName, propertyValue)
    }

    @Override
    DetachedCriteria<T> gtAll(String propertyName, @DelegatesTo(AbstractDetachedCriteria) Closure<?> propertyValue) {
        return (DetachedCriteria<T>)super.gtAll(propertyName, propertyValue)
    }

    @Override
    DetachedCriteria<T> ltAll(String propertyName, @DelegatesTo(AbstractDetachedCriteria) Closure<?> propertyValue) {
        return (DetachedCriteria<T>)super.ltAll(propertyName, propertyValue)
    }

    @Override
    DetachedCriteria<T> geAll(String propertyName, @DelegatesTo(AbstractDetachedCriteria) Closure<?> propertyValue) {
        return (DetachedCriteria<T>)super.geAll(propertyName, propertyValue)
    }

    @Override
    DetachedCriteria<T> leAll(String propertyName, @DelegatesTo(AbstractDetachedCriteria) Closure<?> propertyValue) {
        return (DetachedCriteria<T>)super.leAll(propertyName, propertyValue)
    }

    @Override
    DetachedCriteria<T> eqAll(String propertyName, QueryableCriteria propertyValue) {
        return (DetachedCriteria<T>)super.eqAll(propertyName, propertyValue)
    }

    @Override
    DetachedCriteria<T> gtAll(String propertyName, QueryableCriteria propertyValue) {
        return (DetachedCriteria<T>)super.gtAll(propertyName, propertyValue)
    }

    @Override
    DetachedCriteria<T> gtSome(String propertyName, QueryableCriteria propertyValue) {
        return (DetachedCriteria<T>)super.gtSome(propertyName, propertyValue)
    }

    @Override
    DetachedCriteria<T> gtSome(String propertyName, @DelegatesTo(AbstractDetachedCriteria) Closure<?> propertyValue) {
        return (DetachedCriteria<T>)super.gtSome(propertyName, propertyValue)
    }

    @Override
    DetachedCriteria<T> geSome(String propertyName, QueryableCriteria propertyValue) {
        return (DetachedCriteria<T>)super.geSome(propertyName, propertyValue)
    }

    @Override
    DetachedCriteria<T> geSome(String propertyName, @DelegatesTo(AbstractDetachedCriteria) Closure<?> propertyValue) {
        return (DetachedCriteria<T>)super.geSome(propertyName, propertyValue)
    }

    @Override
    DetachedCriteria<T> ltSome(String propertyName, QueryableCriteria propertyValue) {
        return (DetachedCriteria<T>)super.ltSome(propertyName, propertyValue)
    }

    @Override
    DetachedCriteria<T> ltSome(String propertyName, @DelegatesTo(AbstractDetachedCriteria) Closure<?> propertyValue) {
        return (DetachedCriteria<T>)super.ltSome(propertyName, propertyValue)
    }

    @Override
    DetachedCriteria<T> leSome(String propertyName, QueryableCriteria propertyValue) {
        return (DetachedCriteria<T>)super.leSome(propertyName, propertyValue)
    }

    @Override
    DetachedCriteria<T> leSome(String propertyName, @DelegatesTo(AbstractDetachedCriteria) Closure<?> propertyValue) {
        return (DetachedCriteria<T>)super.leSome(propertyName, propertyValue)
    }

    @Override
    DetachedCriteria<T> ltAll(String propertyName, QueryableCriteria propertyValue) {
        return (DetachedCriteria<T>)super.ltAll(propertyName, propertyValue)
    }

    @Override
    DetachedCriteria<T> geAll(String propertyName, QueryableCriteria propertyValue) {
        return (DetachedCriteria<T>)super.geAll(propertyName, propertyValue)
    }

    @Override
    DetachedCriteria<T> leAll(String propertyName, QueryableCriteria propertyValue) {
        return (DetachedCriteria<T>)super.leAll(propertyName, propertyValue)
    }
    /**
     * Counts the number of records returned by the query
     *
     * @param args The arguments
     * @return The count
     */
    Number count(Map args = Collections.emptyMap(), @DelegatesTo(DetachedCriteria) Closure additionalCriteria = null) {
        (Number)withPopulatedQuery(args, additionalCriteria) { Query query ->
            query.projections().count()
            query.singleResult()
        }
    }

    /**
     * Counts the number of records returned by the query
     *
     * @param args The arguments
     * @return The count
     */
    Number count(@DelegatesTo(DetachedCriteria) Closure additionalCriteria) {
        (Number)withPopulatedQuery(Collections.emptyMap(), additionalCriteria) { Query query ->
            query.projections().count()
            query.singleResult()
        }
    }

    /**
     * Synonym for #count()
     */
    Number size() {
        count()
    }

    /**
     * Counts the number of records returned by the query
     *
     * @param args The arguments
     * @return The count
     */

    boolean asBoolean(@DelegatesTo(DetachedCriteria) Closure additionalCriteria = null) {
        (Boolean)withPopulatedQuery(Collections.emptyMap(), additionalCriteria) { Query query ->
            query.projections().count()
            ((Number)query.singleResult()) > 0
        }
    }

    /**
     * Deletes all entities matching this criteria
     *
     * @return The total number deleted
     */
    Number deleteAll() {
        GormEnhancer.findStaticApi(targetClass, connectionName).withDatastoreSession { Session session ->
            applyLazyCriteria()
            session.deleteAll(this)
        }
    }

    /**
     * Updates all entities matching this criteria
     *
     * @return The total number updated
     */
    Number updateAll(Map properties) {
        GormEnhancer.findStaticApi(targetClass, connectionName).withDatastoreSession { Session session ->
            applyLazyCriteria()
            session.updateAll(this, properties)
        }
    }

    /**
     * Enable the builder syntax for constructing Criteria
     *
     * @param callable The callable closure
     * @return A new criteria instance
     */
    @Override
    DetachedCriteria<T> build(@DelegatesTo(DetachedCriteria) Closure callable) {
        (DetachedCriteria<T>)super.build(callable)
    }

    /**
     * Enable the builder syntax for constructing Criteria
     *
     * @param callable The callable closure
     * @return A new criteria instance
     */
    @Override
    DetachedCriteria<T> buildLazy(@DelegatesTo(DetachedCriteria) Closure callable) {
        (DetachedCriteria<T>)super.buildLazy(callable)
    }

    /**
     * Sets the default max to use and returns a new criteria instance. This method does not mutate the original criteria!
     *
     * @param max The max to use
     * @return A new DetachedCriteria instance derived from this
     */
    @Override
    DetachedCriteria<T> max(int max) {
        (DetachedCriteria<T>)super.max(max)
    }

    /**
     * Sets the default offset to use and returns a new criteria instance. This method does not mutate the original criteria!
     *
     * @param offset The offset to use
     * @return A new DetachedCriteria instance derived from this
     */
    @Override
    DetachedCriteria<T> offset(int offset) {
        (DetachedCriteria<T>)super.offset(offset)
    }

    /**
     * Adds a sort order to this criteria instance
     *
     * @param property The property to sort by
     * @return This criteria instance
     */
    DetachedCriteria<T> sort(String property) {
        (DetachedCriteria<T>)super.sort(property)
    }

    /**
     * Adds a sort order to this criteria instance
     *
     * @param property The property to sort by
     * @param direction The direction to sort by
     * @return This criteria instance
     */
    DetachedCriteria<T> sort(String property, String direction) {
        (DetachedCriteria<T>)super.sort(property,direction)
    }

    /**
     * Adds a property projection
     *
     * @param property The property to project
     * @return This criteria instance
     */
    DetachedCriteria<T> property(String property) {
        (DetachedCriteria<T>)super.property(property)
    }

    /**
     * Adds an id projection
     *
     * @param property The property to project
     * @return This criteria instance
     */
    DetachedCriteria<T> id() {
        (DetachedCriteria<T>)super.id()
    }

    /**
     * Adds a avg projection
     *
     * @param property The property to avg by
     * @return This criteria instance
     */
    DetachedCriteria<T> avg(String property) {
        (DetachedCriteria<T>)super.avg(property)
    }

    /**
     * Adds a sum projection
     *
     * @param property The property to sum by
     * @return This criteria instance
     */
    DetachedCriteria<T> sum(String property) {
        (DetachedCriteria<T>)super.sum(property)
    }

    /**
     * Adds a sum projection
     *
     * @param property The property to min by
     * @return This criteria instance
     */
    DetachedCriteria<T> min(String property) {
        (DetachedCriteria<T>)super.min(property)
    }

    /**
     * Adds a min projection
     *
     * @param property The property to max by
     * @return This criteria instance
     */
    DetachedCriteria<T> max(String property) {
        (DetachedCriteria<T>)super.max(property)
    }

    /**
     * Adds a distinct property projection
     *
     * @param property The property to obtain the distinct value for
     * @return This criteria instance
     */
    DetachedCriteria<T> distinct(String property) {
        (DetachedCriteria<T>)super.distinct(property)
    }


    @Override
    protected DetachedCriteria newInstance() {
        new DetachedCriteria(targetClass, alias)
    }

    @Override
    protected DetachedCriteria<T> clone() {
        return (DetachedCriteria)super.clone()
    }

    protected void handleJunction(Closure callable) {
        try {
            callable.delegate = this
            callable.resolveStrategy = Closure.DELEGATE_FIRST
            callable.call()
        }
        finally {
            def lastJunction = junctions.remove(junctions.size() - 1)
            add lastJunction
        }
    }

    protected QueryableCriteria buildQueryableCriteria(Closure queryClosure) {
        return new DetachedCriteria(targetClass).build(queryClosure)
    }

    private withPopulatedQuery(Map args, Closure additionalCriteria, Closure callable)  {

        GormStaticApi staticApi = persistentEntity.isMultiTenant() ? GormEnhancer.findStaticApi(targetClass) : GormEnhancer.findStaticApi(targetClass, connectionName)
        staticApi.withDatastoreSession { Session session ->
            applyLazyCriteria()
            Query query
            if(alias && (session instanceof QueryAliasAwareSession)) {
                query = session.createQuery(targetClass, alias)
            }
            else {
                query = session.createQuery(targetClass)
            }

            if (defaultMax != null) {
                query.max(defaultMax)
            }
            if (defaultOffset != null) {
                query.offset(defaultOffset)
            }
            DynamicFinder.applyDetachedCriteria(query, this)

            if(query instanceof QueryArgumentsAware) {
                ((QueryArgumentsAware)query).arguments = args
            }

            if (additionalCriteria != null) {
                def additionalDetached = new DetachedCriteria(targetClass).build(additionalCriteria)
                DynamicFinder.applyDetachedCriteria(query, additionalDetached)
            }

            DynamicFinder.populateArgumentsForCriteria(targetClass, query, args)

            callable.call(query)
        }
    }

    protected void applyLazyCriteria() {
        if (lazyQuery == null) {
            return
        }

        def criteria = lazyQuery
        lazyQuery = null
        this.with criteria
    }

}
