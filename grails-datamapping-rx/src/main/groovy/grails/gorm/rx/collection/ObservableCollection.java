package grails.gorm.rx.collection;


import grails.gorm.rx.PersistentObservable;
import rx.Observable;

import java.util.List;

/**
 * @author Graeme Rocher
 * @since 6.0
 */
public interface ObservableCollection<T> extends PersistentObservable<T> {


    /**
     * Returns an Observable for the operation
     *<p>
     * For more information on Observables see the
     * <a href="http://reactivex.io/documentation/observable.html">ReactiveX documentation</a>.
     *</p>
     *
     * @return the Observable for the operation
     */
    Observable<List<T>> toListObservable();

}
