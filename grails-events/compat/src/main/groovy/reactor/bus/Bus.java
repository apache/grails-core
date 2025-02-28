package reactor.bus;

import reactor.bus.registry.Subscription;
import reactor.bus.selector.Selector;
import reactor.fn.Consumer;

/**
 * Basic unit of event handling in Reactor.
 *
 * @author Jon Brisbin
 * @author Stephane Maldini
 * @author Andy Wilkinson
 * @deprecated Here for compatibility only. Do not use directly
 */
@Deprecated
public interface Bus<T> {

    /**
     * Are there any {@link Subscription}s with {@link Selector Selectors} that match the given {@code key}.
     *
     * @param key The key to be matched by {@link Selector Selectors}
     * @return {@literal true} if there are any matching {@literal Subscription}s, {@literal false} otherwise
     */
    boolean respondsToKey(Object key);

    /**
     * Register a {@link reactor.fn.Consumer} to be triggered when a notification matches the given {@link
     * Selector}.
     *
     * @param selector The {@literal Selector} to be used for matching
     * @param consumer The {@literal Consumer} to be triggered
     * @return A {@link Subscription} object that allows the caller to interact with the given mapping
     */
    <V extends T> Subscription<Object, Consumer<? extends T>> on(final Selector selector,
                                                                 final Consumer<V> consumer);


    /**
     * Notify this component that an {@link Event} is ready to be processed.
     *
     * @param key The key to be matched by {@link Selector Selectors}
     * @param ev  The {@literal Event}
     * @return {@literal this}
     */
    Bus notify(Object key, T ev);
}
