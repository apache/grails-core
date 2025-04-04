package grails.gorm.rx.api

import rx.Observable
import rx.Single

/**
 * Interface for Reactive GORM operations on instances
 *
 * @author Graeme Rocher
 * @since 6.0
 */
interface RxGormOperations<D> {

    /**
     * Saves an entity and returns an {@link Observable}, picking either an insert or an update automatically based on whether the object has an id already.
     *
     * @return An {@link Observable} with the result of the operation
     */
    Observable<D> save()

    /**
     * Saves an entity and returns an {@link Observable}, picking either an insert or an update automatically based on whether the object has an id already.
     *
     * @param arguments The arguments to the save method
     *
     * @return An {@link Observable} with the result of the operation
     */
    Observable<D> save(Map<String, Object> arguments)


    /**
     * Saves an entity and returns an {@link Observable}, forcing an insert operation regardless whether an identifier is already present or not
     *
     * @return An {@link Observable} with the result of the operation
     */
    Observable<D> insert()

    /**
     * Saves an entity and returns an {@link Observable}, forcing an insert operation regardless whether an identifier is already present or not
     *
     * @param arguments The arguments to the save method
     *
     * @return An {@link Observable} with the result of the operation
     */
    Observable<D> insert(Map<String, Object> arguments)

    /**
     * Deletes an entity
     *
     * @return An observable that returns a boolean true if successful
     */
    Observable<Boolean> delete()

    /**
     * Deletes an entity
     *
     * @return An observable that returns a boolean true if successful
     */
    Observable<Boolean> delete(Map<String, Object> arguments)
}