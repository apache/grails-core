/* Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.gorm.proxy;

import groovy.lang.DelegatingMetaClass;
import groovy.lang.MetaClass;

import java.io.Serializable;

import org.grails.datastore.mapping.core.Session;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Per-instance metaclass to use for proxied GORM domain objects. It auto-retrieves the associated entity when
 * fields, properties or methods are called (other than those supported by the proxy). The methods and properties
 * supported by the proxy are:
 * <ul>
 *     <li>id/getId() - no resolve performed</li>
 *     <li>initialized/isInitialized() - no resolve performed</li>
 *     <li>class/getClass() - no resolve performed</li>
 *     <li>metaClass/getMetaClass() - no resolve performed</li>
 *     <li>domainClass/getDomainClass() - no resolve performed</li>
 *     <li>target/getTarget() - resolve performed</li>
 *     <li>initialize() - resolve performed</li>
 * </ul>
 * @author Tom Widmer
 */
public class ProxyInstanceMetaClass extends DelegatingMetaClass {
    /**
     * Session to fetch from, if we need to.
     */
    private Session session;
    /**
     * The loaded instance we're proxying, or null if it hasn't been loaded.
     */
    private Object proxyTarget;
    /**
     * The key of the object.
     */
    private Serializable key;

    public ProxyInstanceMetaClass(MetaClass delegate, Session session, Serializable key) {
        super(delegate);
        this.session = session;
        this.key = key;
    }

    /**
     * Load the target from the DB.
     * @return target.
     */
    public Object getProxyTarget() {
        if (proxyTarget == null) {
            proxyTarget = session.retrieve(getTheClass(), getKey());
            if (proxyTarget == null) {
                throw new DataIntegrityViolationException(
                        "Error loading association [" + getKey() + "] of type [" + getTheClass() +
                                "]. Associated instance no longer exists.");
            }
        }

        return proxyTarget;
    }

    /**
     * Handle method calls on our proxy.
     * @param o The proxy.
     * @param methodName
     * @param arguments
     * @return
     */
    @Override
    public Object invokeMethod(Object o, String methodName, Object[] arguments) {
        boolean resolveTarget = true;
        if (methodName.equals("isProxy")) {
            return true;
        } else if (methodName.equals("getId")) {
            return getKey();
        } else if (methodName.equals("isInitialized")) {
            return isProxyInitiated();
        } else if (methodName.equals("getTarget") || methodName.equals("initialize")) {
            return getProxyTarget();
        } else if (methodName.equals("getMetaClass")) {
            return this;
        } else if (methodName.equals("getClass") || methodName.equals("getDomainClass")) {
            // return correct class only if loaded, otherwise hope for the best
            resolveTarget = isProxyInitiated();
        } else if (methodName.equals("setMetaClass") && arguments.length == 1 && (arguments[0]==null || arguments[0] instanceof MetaClass)) {
            resolveTarget = false;
        }
        return delegate.invokeMethod(resolveTarget ? getProxyTarget() : o, methodName, arguments);
    }

    public Serializable getKey() {
        return key;
    }

    public boolean isProxyInitiated() {
        return proxyTarget != null;
    }

    @Override
    public Object getProperty(Object object, String property) {
        if (property.equals("id")) {
            return getKey();
        } else if (property.equals("proxy")) {
            return true;
        } else if (property.equals("initialized")) {
            return isProxyInitiated();
        } else if (property.equals("target")) {
            return getProxyTarget();
        } else if (property.equals("metaClass")) {
            return this;
        } else if (property.equals("class") || property.equals("domainClass")) {
            // return correct class only if loaded, otherwise hope for the best
            return delegate.getProperty(isProxyInitiated() ? proxyTarget : object, property);
        } else {
            return delegate.getProperty(getProxyTarget(), property);
        }
    }

    @Override
    public void setProperty(Object object, String property, Object newValue) {
        boolean resolveTarget = true;
        if(property.equals("metaClass") && (newValue == null || newValue instanceof MetaClass)) {
            resolveTarget = false;
        }        
        delegate.setProperty(resolveTarget ? getProxyTarget() : object, property, newValue);
    }

    @Override
    public Object getAttribute(Object object, String attribute) {
        if (attribute.equals("id")) {
            return getKey();
        } else if (attribute.equals("initialized")) {
            return isProxyInitiated();
        } else if (attribute.equals("target")) {
            return getProxyTarget();
        } else {
            return delegate.getAttribute(getProxyTarget(), attribute);
        }
    }

    @Override
    public void setAttribute(Object object, String attribute, Object newValue) {
        delegate.setAttribute(getProxyTarget(), attribute, newValue);
    }
}
