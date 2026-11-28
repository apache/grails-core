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
package org.grails.forge.build.gradle

import spock.lang.Specification
import spock.lang.Unroll

/**
 * An address of a repository can hold a user and a password, and is not repeated in a message as it
 * was given.
 */
class GradleRepositoryShownAddressSpec extends Specification {

    @Unroll
    void "of #address a message shows #shown"() {
        expect:
        GradleRepository.shownAddress(address) == shown

        where:
        address                                        || shown
        'https://nexus.invalid/releases'               || "'https://nexus.invalid/releases'"
        'https://nexus.invalid:8443/releases/'         || "'https://nexus.invalid:8443/releases/'"
        'HTTPS://nexus.invalid'                        || "'HTTPS://nexus.invalid'"
        'https://alice:Xq7Zk9@nexus.invalid/private'   || "'https://nexus.invalid/private'"
        'https://nexus.invalid/private?token=Xq7Zk9'   || "'https://nexus.invalid/private'"
        'https://nexus.invalid/private#Xq7Zk9'         || "'https://nexus.invalid/private'"
        'http://alice:Xq7Zk9@nexus.invalid/private'    || "'http://nexus.invalid/private'"
        'https://alice:Xq7/Zk9@nexus.invalid/x'        || 'an address that is not shown (https, 37 characters)'
        'https://alice:Xq7#Zk9@nexus.invalid/x'        || 'an address that is not shown (https, 37 characters)'
        'https://alice:Xq7?Zk9@nexus.invalid/x'        || 'an address that is not shown (https, 37 characters)'
        'https://alice:8080/Xq7Zk9@nexus.invalid/x'    || 'an address that is not shown (https, 41 characters)'
        'https://alice:12345?x@nexus.invalid/'         || 'an address that is not shown (https, 36 characters)'
        'https://alice:12345#x@nexus.invalid/'         || 'an address that is not shown (https, 36 characters)'
        'https://alice:12345?x%40nexus.invalid/'       || 'an address that is not shown (https, 38 characters)'
        'https://alice:12345/p?a=b#x@nexus.invalid/'   || 'an address that is not shown (https, 42 characters)'
        'https://bob:pw@alice:12345?x@nexus.invalid/'  || 'an address that is not shown (https, 43 characters)'
        'https://alice:Xq7Zk9%40nexus.invalid/x'       || 'an address that is not shown (https, 38 characters)'
        'https://host.invalid/Xq7Zk9%40nexus.invalid'  || 'an address that is not shown (https, 43 characters)'
        'https:/alice:Xq7Zk9@nexus.invalid/x'          || 'an address that is not shown (https, 35 characters)'
        'https:alice:Xq7Zk9@nexus.invalid/x'           || 'an address that is not shown (https, 34 characters)'
        'https://alice:Xq7 Zk9@nexus.invalid/x'        || 'an address that is not shown (https, 37 characters)'
        'alice:Xq7Zk9@nexus.invalid/x'                 || 'an address that is not shown (28 characters)'
        'file:///srv/maven?token=Xq7Zk9'               || 'an address that is not shown (file, 30 characters)'
        'relative/Xq7Zk9'                              || 'an address that is not shown (15 characters)'
        ''                                             || 'an address that is not shown (0 characters)'
        null                                           || 'an address that was not given'
    }

    @Unroll
    void "what the generator says of the entry #entry of GRAILS_REPO_URL has neither its user nor its password"() {
        when:
        GradleRepository.getDefaultRepositories('9.0.0', entry)

        then:
        IllegalArgumentException e = thrown()
        e.message.startsWith(start)
        !e.message.contains('alice')
        !e.message.contains('Xq7')
        !e.message.contains('Zk9')

        and: 'nor has anything that would be printed with it'
        e.cause == null

        where:
        entry                                         || start
        'http://alice:Xq7Zk9@nexus.invalid/private'   || "Remote GRAILS_REPO_URL repositories must use HTTPS: 'http://nexus.invalid/private'"
        'http://alice:Xq7/Zk9@nexus.invalid/private'  || 'Remote GRAILS_REPO_URL repositories must use HTTPS: an address that is not shown (http, 42 characters)'
        'https://alice:Xq7 Zk9@nexus.invalid/private' || 'Invalid GRAILS_REPO_URL repository: an address that is not shown (https, 43 characters)'
    }

    void "an entry of GRAILS_REPO_URL that the generator takes is written as it was given"() {
        expect:
        GradleRepository.getDefaultRepositories('9.0.0', 'https://nexus.invalid/releases')
                .any { GradleRepository repository -> repository.toSnippet('').contains("url = 'https://nexus.invalid/releases'") }
    }
}
