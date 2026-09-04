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
package org.grails.datastore.gorm.mongo

import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import groovy.transform.CompileStatic

/**
 * Wraps a real {@link MongoClient} so that one driver operation misbehaves, which is the only way to
 * reach conditions the server will not produce on demand — a role that may create an index but not list
 * the existing ones, or a connection that fails while a build is running on a background thread.
 *
 * <p>Everything else is the real driver talking to a real server: only the named method is intercepted,
 * and the databases and collections handed out are wrapped in turn so that the interception survives the
 * calls GORM makes to derive a collection.
 */
@CompileStatic
class FailingMongoClient {

    /**
     * @param delegate the real client to pass everything else through to
     * @param failingMethod the driver method to intercept, by name
     * @param onCall what to do in its place — throw, or block until released
     */
    static MongoClient wrap(MongoClient delegate, String failingMethod, Closure<?> onCall) {
        (MongoClient) proxy(MongoClient, delegate, failingMethod, onCall)
    }

    private static Object proxy(Class<?> iface, Object target, String failingMethod, Closure<?> onCall) {
        Proxy.newProxyInstance(iface.classLoader, [iface] as Class<?>[], new InvocationHandler() {

            @Override
            Object invoke(Object proxyInstance, Method method, Object[] args) {
                if (method.name == failingMethod) {
                    return onCall.call()
                }
                Object result
                try {
                    result = method.invoke(target, args)
                }
                catch (InvocationTargetException e) {
                    throw e.cause
                }
                if (result instanceof MongoDatabase) {
                    return proxy(MongoDatabase, result, failingMethod, onCall)
                }
                if (result instanceof MongoCollection) {
                    return proxy(MongoCollection, result, failingMethod, onCall)
                }
                return result
            }
        })
    }
}
