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
package org.apache.grails.common.aot

import org.springframework.context.aot.AbstractAotProcessor
import org.springframework.core.SpringProperties
import spock.lang.Specification

class AheadOfTimeProcessingSpec extends Specification {

    void cleanup() {
        SpringProperties.setProperty(AbstractAotProcessor.AOT_PROCESSING, null)
    }

    void 'code generation is reported only while spring sets its processing flag'() {
        expect:
        !AheadOfTimeProcessing.isGeneratingCode()

        when:
        SpringProperties.setFlag(AbstractAotProcessor.AOT_PROCESSING)

        then:
        AheadOfTimeProcessing.isGeneratingCode()

        when:
        SpringProperties.setProperty(AbstractAotProcessor.AOT_PROCESSING, 'false')

        then:
        !AheadOfTimeProcessing.isGeneratingCode()
    }

    void 'the helper is a static utility'() {
        expect:
        java.lang.reflect.Modifier.isFinal(AheadOfTimeProcessing.modifiers)
        AheadOfTimeProcessing.declaredConstructors.every { java.lang.reflect.Modifier.isPrivate(it.modifiers) }
    }

}
