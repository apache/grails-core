package grails.gorm.rx.proxy;

import grails.gorm.rx.PersistentObservable;
import org.grails.datastore.mapping.proxy.EntityProxy;

/**
 * An interface for proxied objects to implement that are observable.
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public interface ObservableProxy<T> extends PersistentObservable<T>, EntityProxy<T> {
}
