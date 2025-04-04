package grails.gorm.rx;

import rx.Observable;
import rx.Subscriber;
import rx.Subscription;

/**
 * Common interface for persistent related observables to implement such as collections and proxies
 *
 * @since 6.0
 * @author Graeme Rocher
 */
public interface PersistentObservable<T> {

    /**
     * Returns an Observable for the operation
     *<p>
     * For more information on Observables see the
     * <a href="http://reactivex.io/documentation/observable.html">ReactiveX documentation</a>.
     *</p>
     *
     * @return the Observable for the operation
     */
    Observable<T> toObservable();

    /**
     * A convenience method that subscribes to the Observable as provided by {@link #toObservable}.
     *
     * <p>
     * For more information on Subscriptions see the
     * <a href="http://reactivex.io/documentation/observable.html">ReactiveX documentation</a>.
     *</p>
     *
     * @param subscriber the Subscriber that will handle emissions and notifications from the Observable
     * @return a Subscription reference with which Subscribers that are Observers can
     *         unsubscribe from the Observable
     */
    Subscription subscribe(Subscriber<? super T> subscriber);
}
