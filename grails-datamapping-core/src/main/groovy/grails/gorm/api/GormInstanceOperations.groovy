package grails.gorm.api


/**
 * Instance methods of the GORM API.
 *
 * @author Graeme Rocher
 * @param <D> the entity/domain class
 */
interface GormInstanceOperations<D> {
    /**
     * Allow access to datasource by name
     *
     * @param instance The instance
     * @param name The property name
     * @return The property value
     */
    def propertyMissing(D instance, String name)

    /**
     * Proxy aware instanceOf implementation.
     */
    boolean instanceOf(D instance, Class cls)

    /**
     * Upgrades an existing persistence instance to a write lock
     * @return The instance
     */
    D lock(D instance)

    /**
     * Locks the instance for updates for the scope of the passed closure
     *
     * @param callable The closure
     * @return The result of the closure
     */
    public <T> T mutex(D instance, Closure<T> callable)

    /**
     * Refreshes the state of the current instance
     * @return The instance
     */
    D refresh(D instance)


    /**
     * Saves an object the datastore
     * @return Returns the instance
     */
    D save(D instance)

    /**
     * Forces an insert of an object to the datastore
     * @return Returns the instance
     */
    D insert(D instance)

    /**
     * Forces an insert of an object to the datastore
     * @return Returns the instance
     */
    D insert(D instance, Map params)

    /**
     * Saves an object the datastore
     * @return Returns the instance
     */
    D merge(D instance)

    /**
     * Saves an object the datastore
     * @return Returns the instance
     */
    D merge(D instance, Map params)

    /**
     * Save method that takes a boolean which indicates whether to perform validation or not
     *
     * @param validate Whether to perform validation
     *
     * @return The instance or null if validation fails
     */
    D save(D instance, boolean validate)

    /**
     * Saves an object with the given parameters
     * @param instance The instance
     * @param params The parameters
     * @return The instance
     */
    D save(D instance, Map params)

    /**
     * Returns the objects identifier
     */
    Serializable ident(D instance)

    /**
     * Attaches an instance to an existing session. Requries a session-based model
     * @return
     */
    D attach(D instance)

    /**
     * No concept of session-based model so defaults to true
     */
    boolean isAttached(D instance)

    /**
     * Discards any pending changes. Requires a session-based model.
     */
    void discard(D instance)
    /**
     * Deletes an instance from the datastore
     */
    void delete(D instance)

    /**
     * Deletes an instance from the datastore
     */
    void delete(D instance, Map params)
}