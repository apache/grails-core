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
package org.grails.datastore.mapping.proxy;

import java.io.Serializable;

/**
 * Interface for classes that handle proxies
 *
 * @author Graeme Rocher
 * @since 5.0
 */
public interface ProxyHandler {

    /**
     * Checks whether the specified instance is a proxy
     *
     * @param object The object to check
     * @return True if it is a proxy
     */
    boolean isProxy(Object object);

    /**
     * Checks whether a given proxy is initialized
     *
     * @param object The object to check
     * @return True if it is
     */
    boolean isInitialized(Object object);

    /**
     * Checks whether the given association name of the given object is initialized
     *
     * @param object The object to check The object to check
     * @return True if it is
     */
    boolean isInitialized(Object object, String associationName);

    /**
     * Unwraps the given proxy if it is one
     *
     * @param object The object
     * @return The unwrapped proxy
     */
    Object unwrap(Object object);

    /**
     * Obtains the identifier of an object without initializing the proxy if it is one
     *
     * @param obj The object
     * @return The identifier
     */
    Serializable getIdentifier(Object obj);

    /**
     * Get the class being proxied
     *
     * @param o The object
     * @return The class being proxied
     */
    Class<?> getProxiedClass(Object o);

    /**
     * Initialises the given object
     *
     * @param o The object to initialize
     */
    void initialize(Object o);
}
