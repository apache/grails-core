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
package org.grails.orm.hibernate.proxy;

import java.util.concurrent.Callable;

import org.codehaus.groovy.runtime.InvokerHelper;

import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;

/**
 * ByteBuddy delegation target for {@code getMetaClass()} on the generated Groovy proxy.
 *
 * <p>Hibernate assigns the proxy interceptor after the proxy constructor has run, so any
 * intercepted method invoked while the constructor is still executing answers Hibernate's stub
 * value, which is {@code null} for {@code getMetaClass()}. Groovy trait initializers
 * ({@code $init$}) resolve the receiver's metaclass through {@code getMetaClass()} from inside
 * the entity constructor and fail with a {@code NullPointerException} on that {@code null}.
 *
 * <p>This dispatcher answers the persistent class's {@link groovy.lang.MetaClass} while the
 * interceptor is not assigned yet - the same answer {@link GroovyProxyInterceptorLogic} gives
 * for an uninitialized proxy - and otherwise leaves the proxy's own answer untouched.
 *
 * @since 8.0.1
 */
public class GroovyProxyMetaClassDispatcher {

    private final Class<?> persistentClass;

    public GroovyProxyMetaClassDispatcher(Class<?> persistentClass) {
        this.persistentClass = persistentClass;
    }

    @RuntimeType
    public Object intercept(@SuperCall Callable<?> superCall) throws Throwable {
        final Object metaClass = superCall.call();
        return metaClass != null ? metaClass : InvokerHelper.getMetaClass(persistentClass);
    }
}
