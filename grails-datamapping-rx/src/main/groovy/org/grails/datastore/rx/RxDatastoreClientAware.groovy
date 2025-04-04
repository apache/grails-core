package org.grails.datastore.rx

/**
 * For classes that want to be made aware of the {@link RxDatastoreClient}
 *
 * @author Graeme Rocher
 * @since 6.0
 */
interface RxDatastoreClientAware {
    /**
     * Sets the datastore client
     *
     * @param datastoreClient The datastore client
     */
    void setRxDatastoreClient(RxDatastoreClient datastoreClient)
}