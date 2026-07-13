/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.grails.data.testing.tck.base

import org.grails.datastore.mapping.core.Session
import spock.lang.Specification

/**
 * {@code grails-datamapping-tck} is a shared library of TCK base classes, deliberately configured
 * NOT to run its own tests (see its {@code build.gradle}: "do NOT do test configuration here, this
 * is a TCK module") - every real {@code GrailsDataTckManager} subclass, and its own test coverage,
 * lives in the downstream adapter modules that consume it (here, grails-data-hibernate5-core, which
 * the coverage plan's own constraints specifically call out as the module to verify TCK changes
 * against).
 *
 * The diff added a single new {@code @Deprecated addAllDomainClasses(Collection<Class>)} method - a
 * backward-compatible delegate to the current array-based {@code registerDomainClasses(Class...)}
 * for callers still using the older collection-based signature. Grep confirms no caller anywhere in
 * this repo uses it (every real per-adapter TCK manager, including this module's own
 * {@code GrailsDataHibernate5TckManager}, calls {@code registerDomainClasses} directly) - it's a
 * compatibility shim for external callers, not exercised incidentally by any existing test.
 */
class GrailsDataTckManagerSpec extends Specification {

    static class MinimalTckManager extends GrailsDataTckManager {
        @Override
        Session createSession() { null }
    }

    void "addAllDomainClasses registers each class in the given collection"() {
        given:
        def manager = new MinimalTckManager()

        when:
        manager.addAllDomainClasses([String, Integer])

        then:
        manager.domainClasses as Set == [String, Integer] as Set
    }

    void "addAllDomainClasses is a no-op for a null or empty collection"() {
        given:
        def manager = new MinimalTckManager()

        when:
        manager.addAllDomainClasses(classes)

        then:
        manager.domainClasses.length == 0

        where:
        classes << [null, []]
    }
}
