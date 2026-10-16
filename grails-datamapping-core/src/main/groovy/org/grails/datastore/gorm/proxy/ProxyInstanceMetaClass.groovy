/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.datastore.gorm.proxy

import org.springframework.dao.DataIntegrityViolationException

import org.grails.datastore.mapping.core.Session

/**
 * Per-instance metaclass to use for proxied GORM domain objects. It auto-retrieves the associated entity when
 * fields, properties or methods are called (other than those supported by the proxy). The methods and properties
 * supported by the proxy are:
 * <ul>
 *     <li>id/getId() - no resolve performed</li>
 *     <li>proxy/isProxy() - no resolve performed</li>
 *     <li>initialized/isInitialized() - no resolve performed</li>
 *     <li>class/getClass() - no resolve performed</li>
 *     <li>metaClass/getMetaClass() - no resolve performed</li>
 *     <li>domainClass/getDomainClass() - no resolve performed</li>
 *     <li>target/getTarget() - resolve performed</li>
 *     <li>initialize() - resolve performed</li>
 * </ul>
 * Every {@link MetaClass} dispatch variant applies the same rules, including the sender-aware variants Groovy uses
 * for property assignment and field access on {@code GroovyObject} instances.
 * @author Tom Widmer
 */
class ProxyInstanceMetaClass extends DelegatingMetaClass {

    /**
     * Marker returned by the proxy-aware lookups when a member is not handled by the proxy itself.
     */
    private static final Object DELEGATE = new Object()
    /**
     * Session to fetch from, if we need to.
     */
    private final Session session
    /**
     * The loaded instance we're proxying, or null if it hasn't been loaded.
     */
    private Object proxyTarget
    /**
     * The key of the object.
     */
    private final Serializable key

    ProxyInstanceMetaClass(MetaClass delegate, Session session, Serializable key) {
        super(delegate)
        this.session = session
        this.key = key
    }

    /**
     * Load the target from the DB.
     * @return target.
     */
    @SuppressWarnings('unchecked')
    Object getProxyTarget() {
        if (proxyTarget == null) {
            proxyTarget = session.retrieve(getTheClass(), getKey())
            if (proxyTarget == null) {
                throw new DataIntegrityViolationException(
                        'Error loading association [' + getKey() + '] of type [' + getTheClass() +
                                ']. Associated instance no longer exists.')
            }
        }

        return proxyTarget
    }

    /**
     * Handle method calls on our proxy.
     * @param o The proxy.
     * @param methodName The name of the method being invoked.
     * @param arguments The arguments passed to the method.
     * @return The result of invoking the method, resolving the proxy target first if required.
     */
    @Override
    Object invokeMethod(Object o, String methodName, Object[] arguments) {
        Object result = proxyMethodResult(methodName)
        if (result != DELEGATE) {
            return result
        }
        return delegate.invokeMethod(methodReceiver(o, methodName, arguments), methodName, arguments)
    }

    @Override
    Object invokeMethod(Class sender, Object receiver, String methodName, Object[] arguments,
            boolean isCallToSuper, boolean fromInsideClass) {
        Object result = proxyMethodResult(methodName)
        if (result != DELEGATE) {
            return result
        }
        return delegate.invokeMethod(sender, methodReceiver(receiver, methodName, arguments), methodName, arguments,
                isCallToSuper, fromInsideClass)
    }

    private Object proxyMethodResult(String methodName) {
        return switch (methodName) {
            case 'isProxy' -> true
            case 'getId' -> getKey()
            case 'isInitialized' -> isProxyInitiated()
            case 'getTarget', 'initialize' -> getProxyTarget()
            case 'getMetaClass' -> this
            default -> DELEGATE
        }
    }

    private Object methodReceiver(Object proxy, String methodName, Object[] arguments) {
        if (methodName.equals('getClass') || methodName.equals('getDomainClass')) {
            // return correct class only if loaded, otherwise hope for the best
            return isProxyInitiated() ? proxyTarget : proxy
        }
        if (methodName.equals('setMetaClass') && arguments.length == 1 &&
                (arguments[0] == null || arguments[0] instanceof MetaClass)) {
            return proxy
        }
        return getProxyTarget()
    }

    Serializable getKey() {
        return key
    }

    boolean isProxyInitiated() {
        return proxyTarget != null
    }

    @Override
    Object getProperty(Object object, String property) {
        Object result = proxyPropertyValue(property)
        if (result != DELEGATE) {
            return result
        }
        return delegate.getProperty(propertyReceiver(object, property), property)
    }

    @Override
    Object getProperty(Class sender, Object object, String property, boolean useSuper, boolean fromInsideClass) {
        Object result = proxyPropertyValue(property)
        if (result != DELEGATE) {
            return result
        }
        return delegate.getProperty(sender, propertyReceiver(object, property), property, useSuper, fromInsideClass)
    }

    private Object proxyPropertyValue(String property) {
        return switch (property) {
            case 'id' -> getKey()
            case 'proxy' -> true
            case 'initialized' -> isProxyInitiated()
            case 'target' -> getProxyTarget()
            case 'metaClass' -> this
            default -> DELEGATE
        }
    }

    private Object propertyReceiver(Object proxy, String property) {
        if (property.equals('class') || property.equals('domainClass')) {
            // return correct class only if loaded, otherwise hope for the best
            return isProxyInitiated() ? proxyTarget : proxy
        }
        return getProxyTarget()
    }

    @Override
    void setProperty(Object object, String property, Object newValue) {
        delegate.setProperty(setPropertyReceiver(object, property, newValue), property, newValue)
    }

    @Override
    void setProperty(Class sender, Object object, String property, Object newValue, boolean useSuper,
            boolean fromInsideClass) {
        delegate.setProperty(sender, setPropertyReceiver(object, property, newValue), property, newValue, useSuper,
                fromInsideClass)
    }

    private Object setPropertyReceiver(Object proxy, String property, Object newValue) {
        boolean replacingMetaClass = property.equals('metaClass') && (newValue == null || newValue instanceof MetaClass)
        return replacingMetaClass ? proxy : getProxyTarget()
    }

    @Override
    Object getAttribute(Object object, String attribute) {
        Object result = proxyAttributeValue(attribute)
        if (result != DELEGATE) {
            return result
        }
        return delegate.getAttribute(getProxyTarget(), attribute)
    }

    @Override
    Object getAttribute(Class sender, Object object, String attribute, boolean useSuper) {
        Object result = proxyAttributeValue(attribute)
        if (result != DELEGATE) {
            return result
        }
        return delegate.getAttribute(sender, getProxyTarget(), attribute, useSuper)
    }

    private Object proxyAttributeValue(String attribute) {
        return switch (attribute) {
            case 'id' -> getKey()
            case 'initialized' -> isProxyInitiated()
            case 'target' -> getProxyTarget()
            default -> DELEGATE
        }
    }

    @Override
    void setAttribute(Object object, String attribute, Object newValue) {
        delegate.setAttribute(getProxyTarget(), attribute, newValue)
    }

    @Override
    void setAttribute(Class sender, Object object, String attribute, Object newValue, boolean useSuper,
            boolean fromInsideClass) {
        delegate.setAttribute(sender, getProxyTarget(), attribute, newValue, useSuper, fromInsideClass)
    }
}
