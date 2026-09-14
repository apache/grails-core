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
package org.grails.cli.compiler.grape

import groovy.transform.CompileStatic
import org.eclipse.aether.repository.Proxy
import org.eclipse.aether.repository.ProxySelector
import org.eclipse.aether.repository.RemoteRepository

/**
 * Composite {@link ProxySelector}.
 *
 * @author Dave Syer
 * @since 1.1.0
 */
@CompileStatic
class CompositeProxySelector implements ProxySelector {

    private final List<ProxySelector> selectors

    CompositeProxySelector(List<ProxySelector> selectors) {
        this.selectors = selectors
    }

    @Override
    Proxy getProxy(RemoteRepository repository) {
        for (ProxySelector selector in this.selectors) {
            Proxy proxy = selector.getProxy(repository)
            if (proxy != null) {
                return proxy
            }
        }
        return null
    }

}
