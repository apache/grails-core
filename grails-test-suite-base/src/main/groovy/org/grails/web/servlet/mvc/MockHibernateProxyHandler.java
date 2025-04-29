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

package org.grails.web.servlet.mvc;

import grails.core.support.proxy.EntityProxyHandler;

public class MockHibernateProxyHandler implements EntityProxyHandler {
    public boolean isProxy(Object o) {
        return false;
    }

    public Object unwrapIfProxy(Object instance) {
        return null;
    }

    public boolean isInitialized(Object o) {
        return false;
    }

    public void initialize(Object o) {
    }

    public boolean isInitialized(Object obj, String associationName) {
        return false;
    }

    public Object getProxyIdentifier(Object o) {
        return null;
    }

    public Class<?> getProxiedClass(Object o) {
        return null;
    }
}
