package grails.gorm.rx

import groovy.transform.InheritConstructors
import org.grails.datastore.gorm.query.criteria.AbstractCriteriaBuilder
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.QueryCreator
import org.grails.datastore.mapping.query.api.Criteria
import org.grails.datastore.rx.query.RxQuery
import rx.Observable

import static org.grails.datastore.gorm.finders.DynamicFinder.populateArgumentsForCriteria

/**
 * A CriteriaBuilder implementation for RxGORM
 *
 * @see grails.gorm.CriteriaBuilder
 *
 * @author Graeme Rocher
 * @since 6.0
 */
class CriteriaBuilder<T> extends AbstractCriteriaBuilder {

    CriteriaBuilder(Class<T> targetClass, QueryCreator queryCreator, MappingContext mappingContext) {
        super(targetClass, queryCreator, mappingContext)
    }

    /**
     * Finds a single result matching this criteria. Note that the observable returned will emit each result one by one. If you
     * prefer to receive the entire list of results use {@link #toList()} instead
     *
     * @param callable Any additional criteria
     *
     * @return An observable
     */
    Observable<T> get(@DelegatesTo(CriteriaBuilder) Closure callable) {
        ensureQueryIsInitialized();
        invokeClosureNode(callable);

        uniqueResult = true;
        return ((RxQuery)query).singleResult()
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
    Observable<T> get() {
        ensureQueryIsInitialized()
        uniqueResult = true
        return (Observable<T>)query.singleResult()
    }

    /**
     * Finds a single result matching this criteria. Note that the observable returned will emit each result one by one. If you
     * prefer to receive the entire list of results use {@link #toList()} instead
     *
     * @param callable Any additional criteria
     *
     * @return An observable
     */
    Observable<T> find(@DelegatesTo(CriteriaBuilder) Closure callable = null) {
        return get(callable)
    }

    /**
     * Finds a multiple results matching this criteria. Note that the observable returned will emit each result one by one. If you
     * prefer to receive the entire list of results use {@link #toList()} instead
     *
     * @param additionalCriteria Any additional criteria
     *
     * @return An observable
     */
    Observable<T> findAll(@DelegatesTo(CriteriaBuilder) Closure additionalCriteria) {
        findAll (Collections.emptyMap(), additionalCriteria)
    }

    /**
     * Finds all results for this criteria
     *
     * @param args The arguments
     * @param additionalCriteria Any additional criteria
     * @return An observable that emits a list
     */
    Observable<T> findAll(Map args = Collections.emptyMap(), @DelegatesTo(CriteriaBuilder) Closure additionalCriteria = null) {
        prepareQuery(args, additionalCriteria)

        return ((RxQuery) query).findAll(args)
    }

    /**
     * Converts the observable to another observable that outputs the complete list. Not for use with large datasets
     *
     * @param args The arguments
     * @param additionalCriteria Any additional criteria
     * @return An observable that emits a list
     */
    Observable<List<T>> list(Map args = Collections.emptyMap(), @DelegatesTo(CriteriaBuilder) Closure additionalCriteria = null) {
        findAll(args, additionalCriteria).toList()
    }

    /**
     * Converts the observable to another observable that outputs the complete list. Not for use with large datasets
     *
     * @param additionalCriteria Any additional criteria
     * @return An observable that emits a list
     */
    Observable<List<T>> list(@DelegatesTo(CriteriaBuilder) Closure additionalCriteria ) {
        findAll(Collections.emptyMap(), additionalCriteria).toList()
    }

    /**
     * Calculates the total number of matches for the query
     *
     * @param args The arguments
     * @param additionalCriteria Any additional criteria
     * @return The total results
     */
    Observable<Number> count(Map args, @DelegatesTo(CriteriaBuilder) Closure additionalCriteria = null) {
        Query query = prepareQuery(args, additionalCriteria)
        query.projections().count()
        return ((RxQuery)query).singleResult(args)
    }


    /**
     * Calculates the total number of matches for the query
     *
     * @param args The arguments
     * @param additionalCriteria Any additional criteria
     * @return The total results
     */
    Observable<Number> count(@DelegatesTo(CriteriaBuilder) Closure additionalCriteria) {
        return count(Collections.emptyMap(), additionalCriteria)
    }

    Observable<List<T>> listDistinct(Map args = Collections.emptyMap(), @DelegatesTo(CriteriaBuilder) Closure additionalCriteria = null) {
        prepareQuery(args, additionalCriteria)
        query.projections().distinct();

        ((RxQuery)query).findAll(args).toList()
    }

    Observable<List> listDistinct(@DelegatesTo(CriteriaBuilder) Closure callable) {
        listDistinct(Collections.emptyMap(), callable)
    }


    protected void prepareQuery(Map args, Closure additionalCriteria) {
        ensureQueryIsInitialized()
        populateArgumentsForCriteria(targetClass, query, args);
        invokeClosureNode(additionalCriteria)
        for (Query.Order orderEntry : orderEntries) {
            query.order(orderEntry);
        }
    }

    @Override
    protected Object invokeList() {
        return ((RxQuery)query).findAll()
    }
}
