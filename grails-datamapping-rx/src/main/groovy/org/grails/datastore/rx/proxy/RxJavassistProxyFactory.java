package org.grails.datastore.rx.proxy;

import grails.gorm.rx.proxy.ObservableProxy;
import groovy.lang.GroovyObject;
import javassist.util.proxy.MethodFilter;
import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyObject;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.engine.AssociationQueryExecutor;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.reflect.ReflectionUtils;
import org.grails.datastore.rx.RxDatastoreClient;
import org.grails.datastore.rx.query.QueryState;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates observable proxy instances using Javassist
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public class RxJavassistProxyFactory implements ProxyFactory, org.grails.datastore.mapping.proxy.ProxyFactory {
    private static final Map<Class, Class > PROXY_FACTORIES = new ConcurrentHashMap<Class, Class >();
    private static final Set<String> EXCLUDES = new HashSet(Arrays.asList("$getStaticMetaClass"));
    private static final Class[] EMPTY_CLASS_ARRAY = {};

    @Override
    public <T> T createProxy(RxDatastoreClient client, QueryState queryState, Class<T> type, Serializable key) {
        Class proxyClass = getProxyClass(type);
        MethodHandler mi = createMethodHandler(client, type, proxyClass, key, queryState);
        Object proxy = ReflectionUtils.instantiate(proxyClass);
        ((ProxyObject)proxy).setHandler(mi);
        return (T)proxy;
    }

    @Override
    public ObservableProxy createProxy(RxDatastoreClient client, QueryState queryState, Query query) {
        Class proxyClass = getProxyClass(query.getEntity().getJavaClass());
        MethodHandler mi = createMethodHandler(proxyClass, client, query, queryState);
        Object proxy = ReflectionUtils.instantiate(proxyClass);
        ((ProxyObject)proxy).setHandler(mi);
        return (ObservableProxy) proxy;
    }

    protected MethodHandler createMethodHandler(Class proxyClass, RxDatastoreClient client, Query query, QueryState queryState) {
        return new IdQueryObservableProxyMethodHandler(proxyClass, query, queryState, client);
    }

    protected <T> MethodHandler createMethodHandler(RxDatastoreClient client, Class<T> type, Class proxyClass, Serializable key, QueryState queryState) {
        return new IdentifierObservableProxyMethodHandler(proxyClass, type, key, client, queryState);
    }

    @Override
    public boolean isProxy(Object object) {
        return object instanceof ObservableProxy;
    }

    @Override
    public boolean isInitialized(Object object) {
        return !isProxy(object) || ((ObservableProxy)object).isInitialized();
    }

    @Override
    public boolean isInitialized(Object object, String associationName) {
        return !isProxy(object) || ((ObservableProxy)object).isInitialized();
    }

    @Override
    public Object unwrap(Object object) {
        return isProxy(object) ? ((ObservableProxy)object).getTarget() : object;
    }

    @Override
    public Serializable getIdentifier(Object o) {
        if(isProxy(o)) {
            return ((ObservableProxy)o).getProxyKey();
        }
        return null;
    }

    @Override
    public Class<?> getProxiedClass(Object o) {
        if(isProxy(o)) {
            return o.getClass().getSuperclass();
        }
        return o.getClass();

    }

    @Override
    public void initialize(Object o) {
        if(isProxy(o)) {
            ((ObservableProxy)o).initialize();
        }
    }


    protected <T> Class<T> getProxyClass(Class<T> type) {

        Class proxyClass = PROXY_FACTORIES.get(type);
        if (proxyClass == null) {
            javassist.util.proxy.ProxyFactory pf = new javassist.util.proxy.ProxyFactory();
            pf.setSuperclass(type);
            pf.setInterfaces(getProxyInterfaces());
            pf.setFilter(new MethodFilter() {
                public boolean isHandled(Method method) {
                    final String methodName = method.getName();
                    if (methodName.indexOf("super$") > -1) {
                        return false;
                    }
                    if (method.getParameterTypes().length == 0 && (methodName.equals("finalize"))) {
                        return false;
                    }
                    if (EXCLUDES.contains(methodName) || method.isSynthetic() || method.isBridge()) {
                        return false;
                    }
                    return true;
                }
            });
            proxyClass = pf.createClass();
            PROXY_FACTORIES.put(type, proxyClass);
        }
        return proxyClass;
    }

    protected Class[] getProxyInterfaces() {
        return new Class[]{ ObservableProxy.class, GroovyObject.class };
    }

    @Override
    public <T> T createProxy(Session session, Class<T> type, Serializable key) {
        throw new UnsupportedOperationException("Cannot create proxy using session in stateless mode");
    }

    @Override
    public <T, K extends Serializable> T createProxy(Session session, AssociationQueryExecutor<K, T> executor, K associationKey) {
        throw new UnsupportedOperationException("Cannot create proxy using session in stateless mode");
    }
}
