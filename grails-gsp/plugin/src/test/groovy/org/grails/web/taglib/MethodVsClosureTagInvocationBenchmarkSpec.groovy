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
package org.grails.web.taglib

import grails.artefact.Artefact
import grails.gsp.Tag
import grails.testing.web.taglib.TagLibUnitTest
import spock.lang.Specification

class MethodVsClosureTagInvocationBenchmarkSpec extends Specification implements TagLibUnitTest<MethodVsClosureBenchmarkTagLib> {

    void 'benchmark method invocation versus closure invocation for taglibs'() {
        given:
        int warmupIterations = 50
        int measureIterations = 300
        String closureTemplate = '<g:closureTag value="123" />'
        String methodTemplate = '<g:methodTag value="123" />'

        expect:
        applyTemplate(closureTemplate) == '123'
        applyTemplate(methodTemplate) == '123'

        when:
        warmupIterations.times {
            applyTemplate(closureTemplate)
            applyTemplate(methodTemplate)
        }

        long closureNanos = measureNanos(measureIterations) {
            applyTemplate(closureTemplate)
        }
        long methodNanos = measureNanos(measureIterations) {
            applyTemplate(methodTemplate)
        }

        double closurePerOpMicros = (closureNanos / (double) measureIterations) / 1_000d
        double methodPerOpMicros = (methodNanos / (double) measureIterations) / 1_000d
        double ratio = methodPerOpMicros / closurePerOpMicros

        println "BENCHMARK taglib invocation: closure=${String.format('%.3f', closurePerOpMicros)}us/op, method=${String.format('%.3f', methodPerOpMicros)}us/op, method/closure=${String.format('%.3f', ratio)}"

        then:
        closurePerOpMicros > 0d
        methodPerOpMicros > 0d
    }

    private static long measureNanos(int iterations, Closure<?> work) {
        long start = System.nanoTime()
        iterations.times {
            work.call()
        }
        System.nanoTime() - start
    }
}

@Artefact('TagLib')
class MethodVsClosureBenchmarkTagLib {
    Closure closureTag = { attrs ->
        out << attrs.value
    }

    @Tag
    def methodTag(String value) {
        out << value
    }
}
