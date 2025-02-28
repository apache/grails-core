package grails.events

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.slf4j.LoggerFactory
import reactor.bus.Bus
import reactor.bus.Event
import reactor.bus.EventBus
import reactor.bus.registry.Registration
import reactor.bus.registry.Subscription
import reactor.bus.selector.Selector
import reactor.fn.Consumer

/**
 * Bridges the OLD API to the new
 * @deprecated Here for compatibility only. Do not use directly
 */
@Deprecated
@CompileStatic
trait Events {

    EventBus eventBus

    /**
     * @see #on(reactor.bus.selector.Selector, reactor.fn.Consumer)
     */
    def <E extends Event<?>> Registration<Object, Consumer<E>> on(Class key, Closure consumer) {
        LoggerFactory.getLogger(getClass()).warn("The class [${getClass()}] used the legacy Reactor 2 event bus and needs to be re-compiled")
        on key.name, consumer
    }

    /**
     * @see #on(reactor.bus.selector.Selector, reactor.fn.Consumer)
     */
    def <E extends Event<?> > Registration<Object, Consumer<E>> on(Selector key, Closure consumer) {
        throw new UnsupportedOperationException("Events of type [Selector] are no longer supported. Use string ids")
    }

    /**
     * @see #on(reactor.bus.selector.Selector, reactor.fn.Consumer)
     */
    def <E extends Event<?> > Registration<Object, Consumer<E>> on(key, Closure consumer) {
        LoggerFactory.getLogger(getClass()).warn("The class [${getClass()}] used the legacy Reactor 2 event bus and needs to be re-compiled")

        eventBus.on(key.toString(), consumer) as Registration
    }

    /**
     * @see #on(reactor.bus.selector.Selector, reactor.fn.Consumer)
     */
    def <E extends Event<?> > Registration<Object, Consumer<E>> on(key, Consumer<E> consumer) {
        LoggerFactory.getLogger(getClass()).warn("The class [${getClass()}] used the legacy Reactor 2 event bus and needs to be re-compiled")
        if(key instanceof CharSequence) {
            key = key.toString()
        }
        on(key) {
            consumer.accept(it as E)
        }
    }

    /**
     * @see #on(reactor.bus.selector.Selector, reactor.fn.Consumer)
     */
    def <E extends Event<?> > Registration<Object, Consumer<E>> on(Class type, Consumer<E> consumer) {
        LoggerFactory.getLogger(getClass()).warn("The class [${getClass()}] used the legacy Reactor 2 event bus and needs to be re-compiled")
        on(type.name, consumer)
    }

    /**
     * Register a {@link reactor.fn.Consumer} to be triggered when a notification matches the given {@link
     * Selector}.
     *
     * @param sel
     * 		The {@literal Selector} to be used for matching
     * @param consumer
     * 		The {@literal Consumer} to be triggered
     * @param <E>
     * 		The type of the {@link Event}
     *
     * @return A {@link Subscription} object that allows the caller to interact with the given mapping
     */
    def <E extends Event<?> > Registration<Object, Consumer<E>> on(Selector sel, Consumer<E> consumer) {
        throw new UnsupportedOperationException("Events of type [Selector] are no longer supported. Use string ids")
    }

    /**
     * @see reactor.bus.Bus#notify(java.lang.Object, java.lang.Object)
     */
    @CompileDynamic
    Bus notify(Object key, Event<?> ev) {
        LoggerFactory.getLogger(getClass()).warn("The class [${getClass()}] used the legacy Reactor 2 event bus and needs to be re-compiled")
        if(eventBus == null) throw new IllegalStateException("EventBus not present. Event notification attempted outside of application context.")
        if(ev.replyTo) {
            eventBus.sendAndReceive( ev ) {
                eventBus.notify(ev.replyTo.toString(), it)
            }
        }
        else {
            eventBus.notify key, ev
        }
        return eventBus
    }

    /**
     * @see reactor.bus.Bus#notify(java.lang.Object, reactor.bus.Event)
     */
    Bus notify(Object key, data) {
        LoggerFactory.getLogger(getClass()).warn("The class [${getClass()}] used the legacy Reactor 2 event bus and needs to be re-compiled")
        eventBus.notify Event.from(key.toString(), data)
        return eventBus
    }

    def <E extends Event<?>> Bus notify(Object key, Closure<E> supplier) {
        if(eventBus == null) throw new IllegalStateException("EventBus not present. Event notification attempted outside of application context.")
        LoggerFactory.getLogger(getClass()).warn("The class [${getClass()}] used the legacy Reactor 2 event bus and needs to be re-compiled")
        eventBus.notify( (CharSequence)key.toString(), supplier.call() )
        return eventBus
    }

    Bus sendAndReceive(Object key, data, Closure reply) {
        LoggerFactory.getLogger(getClass()).warn("The class [${getClass()}] used the legacy Reactor 2 event bus and needs to be re-compiled")
        eventBus.sendAndReceive(key.toString(), data, reply)
        return eventBus
    }

    def <E extends Event<?>>    Bus sendAndReceive(Object key, Closure reply) {
        LoggerFactory.getLogger(getClass()).warn("The class [${getClass()}] used the legacy Reactor 2 event bus and needs to be re-compiled")
        eventBus.sendAndReceive key.toString(), new grails.events.Event(key.toString(), new Object[0]), reply
        return eventBus
    }



    /**
     * Creates an {@link Event} for the given data
     *
     * @param data The data
     * @return The event
     */
    def <T> Event<T> eventFor(T data) {
        throw new UnsupportedOperationException("Use [grails.events.Event] instead")
    }

    /**
     * Creates an {@link Event} for the given headers and data
     *
     * @param headers The headers
     * @param data The data
     * @return The event
     */
    def <T> Event<T> eventFor(Map<String, Object> headers, T data) {
        throw new UnsupportedOperationException("Use [grails.events.Event] instead")
    }

    /**
     * Creates an {@link Event} for the given headers, data and error consumer
     *
     * @param headers The headers
     * @param data The data
     * @param errorConsumer The errors consumer
     * @return The event
     */
    def <T> Event<T> eventFor(Map<String, Object> headers, T data, Closure<Throwable> errorConsumer) {
        throw new UnsupportedOperationException("Use [grails.events.Event] instead")
    }

    /**
     * Clears event consumers for the given key
     * @param key The key
     * @return True if modifications were made
     */
    boolean clearEventConsumers(key) {
        LoggerFactory.getLogger(getClass()).warn("The class [${getClass()}] used the legacy Reactor 2 event bus and needs to be re-compiled")
        if(eventBus) {
            eventBus.unsubscribeAll(key.toString())
            return true
        }
        return false
    }

}