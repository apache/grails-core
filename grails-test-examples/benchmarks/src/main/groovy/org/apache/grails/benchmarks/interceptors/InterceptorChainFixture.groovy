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
package org.apache.grails.benchmarks.interceptors

import grails.artefact.Interceptor

/**
 * Interceptor chains for {@code InterceptorChainBenchmark}.
 *
 * Three distinct classes, so that a chain of three is as polymorphic at the adapter's call sites as
 * a real application's chain is. Each matches every request and leaves {@code before()} and
 * {@code after()} at the trait defaults, because what is measured is the adapter's per-interceptor
 * per-phase overhead, not the body of anybody's interceptor.
 */
class InterceptorChainFixture {

    static Interceptor[] createMatchingInterceptors(int count) {
        List<Interceptor> created = [new ChainAuditInterceptor(), new ChainSecurityInterceptor(), new ChainTimingInterceptor()]
        if (count > created.size()) {
            throw new IllegalArgumentException("The fixture only defines ${created.size()} interceptor classes, asked for ${count}")
        }
        created = created.take(count)
        created.each { Interceptor interceptor -> interceptor.matchAll() }
        created as Interceptor[]
    }
}

class ChainAuditInterceptor implements Interceptor {

}

class ChainSecurityInterceptor implements Interceptor {

}

class ChainTimingInterceptor implements Interceptor {

}
