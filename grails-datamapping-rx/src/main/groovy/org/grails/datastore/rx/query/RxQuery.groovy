package org.grails.datastore.rx.query

import groovy.transform.CompileStatic
import rx.Observable

/**
 * Represents a reactive query implementation in RxGORM
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
interface RxQuery<T> {


    /**
     * @return All results matching this query as an observable
     */
    Observable<T> findAll()

    /**
     * @param queryArguments The query arguments. These are things like the max, offset etc.
     *
     * @return All results matching this query as an observable
     */
    Observable<T> findAll(Map<String,Object> queryArguments)

    /**
     *
     * @return A single result matching this query as an observable
     */

    Observable<T> singleResult()

    /**
     * @param queryArguments The query arguments. These are things like the max, offset etc.
     *
     * @return A single result matching this query as an observable
     */

    Observable<T> singleResult(Map<String,Object> queryArguments)

    /**
     * update all entities matching this query with the given properties
     *
     * @param properties The properties
     *
     * @return An observable with the number of entities updated
     */
    Observable<Number> updateAll(Map properties)

    /**
     * delete all entities matching this query
     *
     *
     * @return An observable with the number of entities updated
     */
    Observable<Number> deleteAll()
}
