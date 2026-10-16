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
package org.grails.datastore.mapping.core.grailsversion

import spock.lang.Specification

/**
 * Covers the branch of {@link GrailsVersion#getCurrent()} that resolves the running Grails version. The version is
 * read from the manifest of the artifact that ships {@code grails.util.BuildSettings}, which reaches this module's
 * runtime classpath through its declared dependency on {@code grails-core}. The hermetic
 * {@code GrailsVersionSpec} in {@code grails-datastore-core} covers the unresolved branch.
 */
class GrailsVersionCurrentSpec extends Specification {

    void "getCurrent resolves the running Grails version from BuildSettings"() {
        expect:
        GrailsVersion.current != null
        GrailsVersion.current.major >= 8
        GrailsVersion.current.versionText == Class.forName('grails.util.BuildSettings').package.implementationVersion
    }

    void "the current version based checks compare against the resolved version"() {
        expect:
        GrailsVersion.isAtLeast('3.3.0')
        GrailsVersion.isAtLeastMajorMinor(3, 3)
        !GrailsVersion.isAtLeast('99.9.9')
        !GrailsVersion.isAtLeastMajorMinor(99, 0)
    }
}
