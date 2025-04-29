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
package org.grails.cli.interactive.completers

import spock.lang.*

class RegexCompletorSpec extends Specification {
    @Unroll("String '#source' is not matching")
    def "Simple pattern matches"() {
        given: "a regex completor and an empty candidate list"
        def completor = new RegexCompletor(/!\w+/)
        def candidateList = []

        when: "the completor is invoked for a given string"
        def retval = completor.complete(source, 0, candidateList)

        then: "that string is the sole candidate and the return value is 0"
        candidateList.size() == 1
        candidateList[0] == source
        retval == 0

        where:
        source << [ "!ls", "!test_stuff" ]
    }

    @Unroll("String '#source' is incorrectly matching")
    def "Non matching strings"() {
        given: "a regex completor and an empty candidate list"
        def completor = new RegexCompletor(/!\w+/)
        def candidateList = []

        when: "the completor is invoked for a given (non-matching) string"
        def retval = completor.complete(source, 0, candidateList)

        then: "the candidate list is empty and the return value is -1"
        candidateList.size() == 0
        retval == -1

        where:
        source << [ "!ls ls", "!", "test", "" ]
    }
}
