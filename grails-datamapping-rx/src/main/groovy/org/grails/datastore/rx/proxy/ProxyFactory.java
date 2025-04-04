package org.grails.datastore.rx.proxy;

import grails.gorm.rx.proxy.ObservableProxy;
import org.grails.datastore.mapping.proxy.ProxyHandler;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.rx.RxDatastoreClient;
import org.grails.datastore.rx.query.QueryState;

import java.io.Serializable;

/**
 * @author Graeme Rocher
 * @since 6.0
 */
public interface ProxyFactory extends ProxyHandler {


    /**
     * Creates a proxy
     *
     * @param <T> The type of the proxy to create
     * @param client The datastore client
     * @param queryState Any prior query state
     * @param type The type of the proxy to create
     * @param key The key to proxy
     * @return A proxy instance
     */
    <T> T createProxy(RxDatastoreClient client, QueryState queryState, Class<T> type, Serializable key);

    /**
     * Creates a proxy
     *
     * @param client The datastore client
     * @param queryState Any prior query state
     * @param query The query to be executed to initialize the proxy
     *
     * @return A proxy instance
     */
    ObservableProxy createProxy(RxDatastoreClient client, QueryState queryState, Query query);
}
