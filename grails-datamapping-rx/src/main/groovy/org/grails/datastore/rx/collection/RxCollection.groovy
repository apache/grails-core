package org.grails.datastore.rx.collection

import grails.gorm.rx.collection.ObservableCollection
import groovy.transform.CompileStatic
import rx.Observable
import rx.Subscriber
import rx.Subscription

/**
 * A trait that can be implemented by collection types to make them observable
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
trait RxCollection<T> implements ObservableCollection<T>{

    /**
     * The underlying observable
     */
    Observable observable

    /**
     * @return A list observable
     */
    Observable<List> toListObservable() {
        observable.toList()
    }

    Observable toObservable() {
        return this.observable
    }

    Subscription subscribe(Subscriber subscriber) {
        return observable.subscribe(subscriber)
    }
}