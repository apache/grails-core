package grails.gorm.rx

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import org.grails.datastore.gorm.finders.DynamicFinder
import org.grails.datastore.gorm.query.criteria.AbstractDetachedCriteria
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.api.Criteria
import org.grails.datastore.mapping.query.api.ProjectionList
import org.grails.datastore.mapping.query.api.QueryArgumentsAware
import org.grails.datastore.mapping.query.api.QueryableCriteria
import org.grails.datastore.rx.query.RxQuery
import org.grails.gorm.rx.api.RxGormEnhancer
import rx.Observable
import rx.Subscriber
import rx.Subscription

import jakarta.persistence.FetchType

/**
 * Reactive version of {@link grails.gorm.DetachedCriteria}
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class DetachedCriteria<T> extends AbstractDetachedCriteria<Observable<T>> implements PersistentObservable<T> {
    DetachedCriteria(Class<Observable<T>> targetClass, String alias) {
        super(targetClass, alias)
    }

    DetachedCriteria(Class<Observable<T>> targetClass) {
        super(targetClass)
    }

    /**
     * Finds a single result matching this criteria. Note that the observable returned will emit each result one by one. If you
     * prefer to receive the entire list of results use {@link #toList()} instead
     *
     * @param args The arguments The arguments
     * @param additionalCriteria Any additional criteria
     *
     * @return An observable
     */
    Observable<T> find(Map args = Collections.emptyMap(), @DelegatesTo(DetachedCriteria) Closure additionalCriteria = null) {
        Query query = prepareQuery(args, additionalCriteria)
        query.max(1)
        return ((RxQuery)query).findAll(args)
    }

    /**
     * Finds all results matching this criteria. Note that the observable returned will emit each result one by one. If you
     * prefer to receive the entire list of results use {@link #toList()} instead
     *
     * @param args The arguments The arguments
     * @param additionalCriteria Any additional criteria
     *
     * @return An observable
     */
    Observable<T> findAll(Map args = Collections.emptyMap(), @DelegatesTo(DetachedCriteria) Closure additionalCriteria = null) {
        Query query = prepareQuery(args, additionalCriteria)
        return ((RxQuery)query).findAll(args)
    }

    /**
     * The same as {@link #find(java.util.Map, groovy.lang.Closure)}
     *
     * @see #find(java.util.Map, groovy.lang.Closure)
     */
    Observable<T> get(Map args, @DelegatesTo(DetachedCriteria) Closure additionalCriteria = null) {
        Query query = prepareQuery(args, additionalCriteria)
        query.max(1)
        return ((RxQuery)query).singleResult(args)
    }

    /**
     * The same as {@link #find(java.util.Map, groovy.lang.Closure)}
     *
     * @see #find(java.util.Map, groovy.lang.Closure)
     */
    Observable<T> get(@DelegatesTo(DetachedCriteria) Closure additionalCriteria = null) {
        Query query = prepareQuery(Collections.emptyMap(), additionalCriteria)
        query.max(1)
        return ((RxQuery)query).singleResult()
    }

    /**
     * Converts the observable to another observable that outputs the complete list. Not for use with large datasets
     *
     * @param args The arguments
     * @param additionalCriteria Any additional criteria
     * @return An observable that emits a list
     */
    Observable<List<T>> toList(Map args = Collections.emptyMap(), @DelegatesTo(DetachedCriteria) Closure additionalCriteria = null) {
        Query query = prepareQuery(args, additionalCriteria)
        return ((RxQuery)query).findAll(args).toList()
    }

    /**
     * Converts the observable to another observable that outputs the complete list. Not for use with large datasets
     *
     * @param args The arguments
     * @param additionalCriteria Any additional criteria
     * @return An observable that emits a list
     */
    Observable<List<T>> list(Map args = Collections.emptyMap(), @DelegatesTo(DetachedCriteria) Closure additionalCriteria = null) {
        Query query = prepareQuery(args, additionalCriteria)
        return ((RxQuery)query).findAll(args).toList()
    }

    /**
     * Calculates the total number of matches for the query
     *
     * @param args The arguments
     * @param additionalCriteria Any additional criteria
     * @return The total results
     */
    Observable<Number> getCount(Map args = Collections.emptyMap(), @DelegatesTo(DetachedCriteria) Closure additionalCriteria = null) {
        Query query = prepareQuery(args, additionalCriteria)
        query.projections().count()
        return ((RxQuery)query).singleResult(args) as Observable<Number>
    }

    /**
     * The same as {@link #getCount(java.util.Map, groovy.lang.Closure)}
     *
     * @see #getCount(java.util.Map, groovy.lang.Closure)
     */
    Observable<Number> count(Map args = Collections.emptyMap(), @DelegatesTo(DetachedCriteria) Closure additionalCriteria = null) {
        getCount(args, additionalCriteria)
    }

    /**
     * Updates all entities matching this criteria
     *
     * @param propertiesMap The property names and values to update
     *
     * @return An observable that returns the total number updated
     */
    Observable<Number> updateAll(Map propertiesMap) {
        Query query = prepareQuery(Collections.emptyMap(), null)
        return ((RxQuery)query).updateAll(propertiesMap)
    }

    /**
     * Deletes all entities matching this criteria
     *
     * @return An observable that returns the total number deleted
     */
    Observable<Number> deleteAll() {
        Query query = prepareQuery(Collections.emptyMap(), null)
        return ((RxQuery)query).deleteAll()
    }

    /**
     * Convert this {@link DetachedCriteria} to a query implementation
     *
     * @param args The arguments
     * @return The query
     */
    Query toQuery(Map args = Collections.emptyMap()) {
        return prepareQuery(args, null)
    }

    @Override
    DetachedCriteria<T> where(@DelegatesTo(AbstractDetachedCriteria) Closure additionalQuery) {
        return (DetachedCriteria) super.where(additionalQuery)
    }

    @Override
    DetachedCriteria<T> whereLazy(@DelegatesTo(AbstractDetachedCriteria) Closure additionalQuery) {
        return (DetachedCriteria) super.whereLazy(additionalQuery)
    }

    @Override
    DetachedCriteria<T> build(@DelegatesTo(grails.gorm.DetachedCriteria) Closure callable) {
        return (DetachedCriteria)super.build(callable)
    }

    @Override
    DetachedCriteria<T> buildLazy(@DelegatesTo(grails.gorm.DetachedCriteria) Closure callable) {
        return (DetachedCriteria)super.buildLazy(callable)
    }

    @Override
    DetachedCriteria<T> max(int max) {
        return (DetachedCriteria)super.max(max)
    }

    @Override
    DetachedCriteria<T> offset(int offset) {
        return (DetachedCriteria)super.offset(offset)
    }

    @Override
    DetachedCriteria<T> sort(String property) {
        return (DetachedCriteria)super.sort(property)
    }

    @Override
    DetachedCriteria<T> sort(String property, String direction) {
        return (DetachedCriteria)super.sort(property, direction)
    }

    @Override
    DetachedCriteria<T> property(String property) {
        return (DetachedCriteria)super.property(property)
    }

    @Override
    DetachedCriteria<T> id() {
        return (DetachedCriteria)super.id()
    }

    @Override
    DetachedCriteria<T> avg(String property) {
        return (DetachedCriteria)super.avg(property)
    }

    @Override
    DetachedCriteria<T> sum(String property) {
        return (DetachedCriteria)super.sum(property)
    }

    @Override
    DetachedCriteria<T> min(String property) {
        return (DetachedCriteria)super.min(property)
    }

    @Override
    DetachedCriteria<T> max(String property) {
        return (DetachedCriteria)super.max(property)
    }

    @Override
    def propertyMissing(String name) {
        return super.propertyMissing(name)
    }

    @Override
    DetachedCriteria<T> distinct(String property) {
        return (DetachedCriteria)super.distinct(property)
    }

    @Override
    protected QueryableCriteria buildQueryableCriteria(Closure queryClosure) {
        return (QueryableCriteria)new DetachedCriteria(targetClass).build(queryClosure)
    }

    @Override
    protected DetachedCriteria<T> clone() {
        return (DetachedCriteria)super.clone()
    }

    @Override
    protected DetachedCriteria newInstance() {
        new DetachedCriteria(targetClass, alias)
    }

    @Override
    DetachedCriteria<T> join(String property) {
        return (DetachedCriteria<T>)super.join(property)
    }

    @Override
    DetachedCriteria<T> select(String property) {
        return (DetachedCriteria<T>)super.select(property)
    }

    @Override
    DetachedCriteria<T> projections(@DelegatesTo(ProjectionList) Closure callable) {
        return (DetachedCriteria<T>)super.projections(callable)
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
    protected List convertArgumentList(Collection argList) {
        return super.convertArgumentList(argList)
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

    protected Query prepareQuery(Map args, Closure additionalCriteria) {
        def staticApi = RxGormEnhancer.findStaticApi(targetClass)
        applyLazyCriteria()
        def query = staticApi.datastoreClient.createQuery(targetClass, args)

        if (defaultMax != null) {
            query.max(defaultMax)
        }
        if (defaultOffset != null) {
            query.offset(defaultOffset)
        }
        DynamicFinder.applyDetachedCriteria(query, this)

        for(entry in fetchStrategies) {
            switch(entry.value) {
                case FetchType.EAGER:
                    query.join(entry.key)
                break
                default:
                    query.select(entry.key)
            }
        }

        if (query instanceof QueryArgumentsAware) {
            query.arguments = args
        }

        if (additionalCriteria != null) {
            def additionalDetached = new DetachedCriteria(targetClass).build(additionalCriteria)
            DynamicFinder.applyDetachedCriteria(query, additionalDetached)
        }

        DynamicFinder.populateArgumentsForCriteria(targetClass, query, args)
        query
    }

    @Override
    Observable<T> toObservable() {
        findAll()
    }

    @Override
    Subscription subscribe(Subscriber<? super T> subscriber) {
        findAll().subscribe(subscriber)
    }
}
