package grails.events

import groovy.transform.AutoFinal
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

/**
 * Wraps an event
 *
 * @since 3.3
 * @author Graeme Rocher
 */
@CompileStatic
@AutoFinal
@EqualsAndHashCode
@ToString
class Event<T> extends EventObject {
    /**
     * The id of the event
     */
    final String id
    /**
     * The data of the event
     */
    final T data
    /**
     * The parameters for the event
     */
    final Map<String, Object> parameters

    Event(String id, T data) {
        this(id, Collections.emptyMap() as Map<String,Object>, data)
    }

    Event(String id, Map<String, Object> parameters, T data) {
        super(id)
        this.id = id
        this.data = data
        this.parameters = Collections.unmodifiableMap(parameters)
    }

    /**
     * Wrap the given object with an {@link Event}.
     *
     * @param obj
     *     The object to from.
     *
     * @return The new {@link Event}.
     */
    static <T> Event<T> from(String id, T obj) {
        return new Event<T>(id, obj)
    }

    /**
     * Wrap the given object with an {@link Event}.
     *
     * @param obj
     *     The object to from.
     *
     * @return The new {@link Event}.
     */
    static <T> Event<T> from(String id, Map<String, Object> parameters, T obj) {
        return new Event<T>(id, parameters, obj)
    }
}
