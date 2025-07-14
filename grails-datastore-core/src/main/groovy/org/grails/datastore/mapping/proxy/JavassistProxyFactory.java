/* Copyright (C) 2010-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.mapping.proxy;

import groovy.lang.GroovyObject;
import javassist.util.proxy.MethodFilter;
import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;
import javassist.util.proxy.ProxyObject;
import org.codehaus.groovy.transform.trait.Traits;
import org.grails.datastore.mapping.collection.PersistentCollection;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.engine.AssociationQueryExecutor;
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher;
import org.grails.datastore.mapping.reflect.ReflectionUtils;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A proxy factory that uses Javassist to create proxies
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class JavassistProxyFactory implements org.grails.datastore.mapping.proxy.ProxyFactory {

    private static final Map<Class, Class> PROXY_FACTORIES = new ConcurrentHashMap<Class, Class>();
    private static final Map<Class, Class> ID_TYPES = new ConcurrentHashMap<Class, Class>();
    private static final Class[] EMPTY_CLASS_ARRAY = {};

    private static final Set<String> EXCLUDES = new HashSet(Arrays.asList("$getStaticMetaClass"));
    private static final String DATASTORE_PACKAGE_PREFIX = "org.grails.datastore.";
    private static final String DATASTORE_PACKAGE_UNDER_SCORE_PREFIX = DATASTORE_PACKAGE_PREFIX.replace('.', '_');

    public boolean isProxy(Object object) {
        return object instanceof EntityProxy || object instanceof PersistentCollection;
    }

    public Serializable getIdentifier(Object obj) {
        if (obj instanceof EntityProxy) {
            return ((EntityProxy) obj).getProxyKey();
        } else {
            return null;
        }
    }

    @Override
    public Class<?> getProxiedClass(Object o) {
        if (isProxy(o)) {
            return o.getClass().getSuperclass();
        }
        return o.getClass();
    }

    @Override
    public void initialize(Object o) {
        if (o instanceof EntityProxy) {
            ((EntityProxy) o).initialize();
        } else if (o instanceof PersistentCollection) {
            ((PersistentCollection) o).initialize();
        }
    }

    /**
     * Checks whether a given proxy is initialized
     *
     * @param object The object to check
     * @return True if it is
     */
    public boolean isInitialized(Object object) {
        if (!isProxy(object)) {
            return true;
        } else if (object instanceof EntityProxy) {
            return ((EntityProxy) object).isInitialized();
        } else if (object instanceof PersistentCollection) {
            return ((PersistentCollection) object).isInitialized();
        }
        return true;
    }

    @Override
    public boolean isInitialized(Object object, String associationName) {
        final Object value = ClassPropertyFetcher.getInstancePropertyValue(object, associationName);
        return value == null || isInitialized(value);
    }

    /**
     * Unwraps the given proxy if it is one
     *
     * @param object The object
     * @return The unwrapped proxy
     */
    public Object unwrap(Object object) {
        if (isProxy(object) && object instanceof EntityProxy) {
            return ((EntityProxy) object).getTarget();
        }
        return object;
    }

    public <T> T createProxy(Session session, Class<T> type, Serializable key) {
        return (T) getProxyInstance(session, type, key);
    }

    @Override
    public <T, K extends Serializable> T createProxy(Session session, AssociationQueryExecutor<K, T> executor, K associationKey) {
        MethodHandler mi = createMethodHandler(session, executor, associationKey);
        Class proxyClass = getProxyClass(executor.getIndexedEntity().getJavaClass());
        Object proxy = ReflectionUtils.instantiate(proxyClass);
        ((ProxyObject) proxy).setHandler(mi);
        return (T) proxy;
    }

    protected Object createProxiedInstance(final Session session, final Class cls, Class proxyClass, final Serializable id) {
        MethodHandler mi = createMethodHandler(session, cls, proxyClass, id);
        Object proxy = ReflectionUtils.instantiate(proxyClass);
        ((ProxyObject) proxy).setHandler(mi);
        return proxy;
    }

    protected <K extends Serializable, T> MethodHandler createMethodHandler(Session session, AssociationQueryExecutor<K, T> executor, K associationKey) {
        return new AssociationQueryProxyHandler(session, executor, associationKey);
    }

    protected MethodHandler createMethodHandler(Session session, Class cls, Class proxyClass, Serializable id) {
        return new SessionEntityProxyMethodHandler(proxyClass, session, cls, id);
    }

    protected Object getProxyInstance(Session session, Class type, Serializable id) {
        Class proxyClass = getProxyClass(type);
        return createProxiedInstance(session, type, proxyClass, id);
    }

    protected Class getProxyClass(Class type) {

        Class proxyClass = PROXY_FACTORIES.get(type);
        if (proxyClass == null) {
            javassist.util.proxy.ProxyFactory pf = new ProxyFactory();
            pf.setSuperclass(type);
            pf.setInterfaces(getProxyInterfaces());
            pf.setFilter(new MethodFilter() {
                public boolean isHandled(Method method) {
                    Traits.TraitBridge traitBridge = method.getAnnotation(Traits.TraitBridge.class);
                    if (traitBridge != null) {
                        Class traitClass = traitBridge.traitClass();
                        // ignore core traits
                        if (traitClass.getPackage().getName().startsWith(DATASTORE_PACKAGE_PREFIX)) {
                            return false;
                        }
                    }
                    final String methodName = method.getName();
                    if (methodName.contains("super$") || methodName.startsWith(DATASTORE_PACKAGE_UNDER_SCORE_PREFIX)) {
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

            Method getIdMethod = org.springframework.util.ReflectionUtils.findMethod(type, "getId", EMPTY_CLASS_ARRAY);
            Class<?> idType = getIdMethod.getReturnType();
            if (idType != null) {
                ID_TYPES.put(type, idType);
            }
        }
        return proxyClass;
    }

    protected Class[] getProxyInterfaces() {
        return new Class[]{EntityProxy.class, GroovyObject.class};
    }

}
