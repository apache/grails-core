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
package grails.core.support.proxy

import groovy.transform.CompileStatic

/**
 * Trivial default implementation that always returns true and the object.
 *
 * @author Graeme Rocher
 * @since 1.2.2
 */
@CompileStatic
class DefaultProxyHandler implements ProxyHandler {

    boolean isInitialized(Object o) {
        return true
    }

    boolean isInitialized(Object obj, String associationName) {
        return true
    }

    Object unwrapIfProxy(Object instance) {
        return instance
    }

    boolean isProxy(Object o) {
        return false
    }

    void initialize(Object o) {
        // do nothing
    }

}
