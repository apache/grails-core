package org.grails.datastore.rx.proxy;

import org.grails.datastore.rx.RxDatastoreClient;
import org.grails.datastore.rx.exceptions.BlockingOperationException;
import org.grails.datastore.rx.internal.RxDatastoreClientImplementor;
import org.grails.datastore.rx.query.QueryState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.functions.Action1;
import rx.functions.Func1;

import java.io.Serializable;

/**
 * A proxy {@link javassist.util.proxy.MethodHandler} that uses the entity class and identifier to resolve the target
 *
 * @author Graeme Rocher
 * @since 6.0
 */
class IdentifierObservableProxyMethodHandler extends AbstractObservableProxyMethodHandler {

    private static final Logger LOG = LoggerFactory.getLogger(IdentifierObservableProxyMethodHandler.class);
    protected final Serializable proxyKey;
    protected final Observable observable;

    IdentifierObservableProxyMethodHandler(Class<?> proxyClass, Class type, Serializable proxyKey, RxDatastoreClient client, QueryState queryState) {
        super(proxyClass, type, queryState, client);
        this.proxyKey = proxyKey;
        this.observable = resolveObservable();
    }

    protected Observable resolveObservable() {
        Observable observable = ((RxDatastoreClientImplementor) client).get(type, proxyKey, queryState);
        observable.map(new Func1() {
            @Override
            public Object call(Object o) {
                target = o;
                return o;
            }
        });
        return observable;
    }

    @Override
    protected Object resolveDelegate(Object self) {
        if(target != null) {
            return target;
        }

        Object loadedEntity = queryState != null ? queryState.getLoadedEntity(type, proxyKey) : null;
        if(loadedEntity != null) {
            this.target = loadedEntity;
        }
        else {
            if(((RxDatastoreClientImplementor)client).isAllowBlockingOperations()) {
                if(LOG.isWarnEnabled()) {
                    LOG.warn("Entity of type [{}] with id [{}] lazy loaded using a blocking operation. Consider using ObservableProxy.subscribe(..) instead", type.getName(), proxyKey);
                }
                this.target = observable.toBlocking().first();
            }
            else {
                throw new BlockingOperationException("Cannot initialize proxy for class ["+type+"] using a blocking operation. Use ObservableProxy.subscribe(..) instead.");
            }
        }
        return this.target;
    }

    @Override
    protected Object getProxyKey(Object self) {
        return proxyKey;
    }

}
