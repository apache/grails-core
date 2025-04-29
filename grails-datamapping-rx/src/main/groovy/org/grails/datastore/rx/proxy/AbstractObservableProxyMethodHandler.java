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
package org.grails.datastore.rx.proxy;

import org.grails.datastore.mapping.proxy.EntityProxyMethodHandler;
import org.grails.datastore.rx.RxDatastoreClient;
import org.grails.datastore.rx.query.QueryState;
import org.springframework.util.ReflectionUtils;
import rx.Observable;
import rx.Subscriber;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Abstract proxy generator for ObservableProxy instances
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public abstract class AbstractObservableProxyMethodHandler extends EntityProxyMethodHandler {
    protected final Class type;
    protected final RxDatastoreClient client;
    protected final QueryState queryState;
    protected Object target;

    public AbstractObservableProxyMethodHandler(Class<?> proxyClass, Class type, QueryState queryState, RxDatastoreClient client) {
        super(proxyClass);
        this.type = type;
        this.queryState = queryState;
        this.client = client;
    }

    @Override
    protected Object isProxyInitiated(Object self) {
        return target != null;
    }

    protected abstract Observable resolveObservable();

    @Override
    protected Object invokeEntityProxyMethods(Object self, String methodName, Object[] args) {
        if(methodName.equals("subscribe")) {
            Observable observable = resolveObservable();
            return observable.subscribe((Subscriber) args[0]);
        }
        else if(methodName.equals("toObservable")) {
            return resolveObservable();
        }
        else {
            return super.invokeEntityProxyMethods(self, methodName, args);
        }
    }

    protected Object handleInvocationFallback(Object self, Method thisMethod, Object[] args) {
        Object actualTarget = getProxyTarget(self);
        if(!thisMethod.getDeclaringClass().isInstance(actualTarget)) {
            if(Modifier.isPublic(thisMethod.getModifiers())) {
                final Method method = ReflectionUtils.findMethod(actualTarget.getClass(), thisMethod.getName(), thisMethod.getParameterTypes());
                if(method != null) {
                    ReflectionUtils.makeAccessible(method);
                    thisMethod = method;
                }
            } else {
                final Method method = ReflectionUtils.findMethod(actualTarget.getClass(), thisMethod.getName(), thisMethod.getParameterTypes());
                if(method != null) {
                    thisMethod = method;
                }
            }
        }
        return ReflectionUtils.invokeMethod(thisMethod, actualTarget, args);
    }
}
