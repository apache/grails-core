package org.grails.datastore.gorm.proxy;

import grails.core.support.proxy.EntityProxyHandler;
import org.grails.datastore.mapping.proxy.ProxyHandler;

/**
 * Adapts the core Grails proxy handler interface to the GORM one
 *
 * @author Graeme Rocher
 * @since 5.0
 */
public class ProxyHandlerAdapter implements EntityProxyHandler {

    final ProxyHandler delegate;

    public ProxyHandlerAdapter(ProxyHandler delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object getProxyIdentifier(Object o) {
        return delegate.getIdentifier(o);
    }

    @Override
    public Class<?> getProxiedClass(Object o) {
        return delegate.getProxiedClass(o);
    }

    @Override
    public boolean isProxy(Object o) {
        return delegate.isProxy(o);
    }

    @Override
    public Object unwrapIfProxy(Object instance) {
        return delegate.unwrap(instance);
    }

    @Override
    public boolean isInitialized(Object o) {
        return delegate.isInitialized(o);
    }

    @Override
    public void initialize(Object o) {
        delegate.initialize(o);
    }

    @Override
    public boolean isInitialized(Object obj, String associationName) {
        return delegate.isInitialized(obj, associationName);
    }
}
