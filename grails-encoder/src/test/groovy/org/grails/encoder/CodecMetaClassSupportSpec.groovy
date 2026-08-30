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
package org.grails.encoder

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

import org.codehaus.groovy.runtime.InvokerHelper

import grails.util.GrailsMetaClassUtils
import spock.lang.Specification

class CodecMetaClassSupportSpec extends Specification {

    void cleanup() {
        GroovySystem.metaClassRegistry.removeMetaClass(CodecMetaClassSupportSpecTarget)
        GroovySystem.metaClassRegistry.removeMetaClass(CodecMetaClassSupportSecondSpecTarget)
    }

    void 'cached codec registration is idempotent for the same codec factory'() {
        given:
            CodecMetaClassSupport support = new CodecMetaClassSupport()
            BenchmarkCodecFactory codecFactory = new BenchmarkCodecFactory()
            List<ExpandoMetaClass> targetMetaClasses = [GrailsMetaClassUtils.getExpandoMetaClass(CodecMetaClassSupportSpecTarget)]

        when:
            support.configureCodecMethods(codecFactory, true, targetMetaClasses)
            Object firstResult = InvokerHelper.invokeMethod(new CodecMetaClassSupportSpecTarget('a&b'), 'encodeAsBenchmark', null)
            codecFactory.encoder = new BenchmarkEncoder('|')
            support.configureCodecMethods(codecFactory, true, targetMetaClasses)
            Object secondResult = InvokerHelper.invokeMethod(new CodecMetaClassSupportSpecTarget('a&b'), 'encodeAsBenchmark', null)

        then:
            firstResult == 'a&amp;b'
            secondResult == 'a&amp;b'
    }

    void 'cached codec registration is idempotent for decoder and aliases'() {
        given:
            CodecMetaClassSupport support = new CodecMetaClassSupport()
            BenchmarkCodecFactory codecFactory = new BenchmarkCodecFactory()
            List<ExpandoMetaClass> targetMetaClasses = [GrailsMetaClassUtils.getExpandoMetaClass(CodecMetaClassSupportSpecTarget)]

        when:
            support.configureCodecMethods(codecFactory, true, targetMetaClasses)
            support.configureCodecMethods(codecFactory, true, targetMetaClasses)
            Object encodedAlias = InvokerHelper.invokeMethod(new CodecMetaClassSupportSpecTarget('a&b'), 'encodeAsBench', null)
            Object decoded = InvokerHelper.invokeMethod(new CodecMetaClassSupportSpecTarget('a&amp;b'), 'decodeBenchmark', null)
            Object decodedAlias = InvokerHelper.invokeMethod(new CodecMetaClassSupportSpecTarget('a&amp;b'), 'decodeBench', null)

        then:
            encodedAlias == 'a&amp;b'
            decoded == 'a&b'
            decodedAlias == 'a&b'
    }

    void 'cached codec registration is atomic for concurrent same factory registration'() {
        given:
            CodecMetaClassSupport support = new CodecMetaClassSupport()
            BenchmarkCodecFactory codecFactory = new BenchmarkCodecFactory()
            List<ExpandoMetaClass> targetMetaClasses = [GrailsMetaClassUtils.getExpandoMetaClass(CodecMetaClassSupportSpecTarget)]
            CountDownLatch start = new CountDownLatch(1)
            ExecutorService executor = Executors.newFixedThreadPool(8)
            List<Future<Object>> futures = (1..8).collect {
                executor.submit({
                    start.await()
                    support.configureCodecMethods(codecFactory, true, targetMetaClasses)
                    null
                } as Callable<Object>)
            }

        when:
            start.countDown()
            futures*.get(10L, TimeUnit.SECONDS)
            Object concurrentEncode = InvokerHelper.invokeMethod(new CodecMetaClassSupportSpecTarget('a&b'), 'encodeAsBenchmark', null)
            Object concurrentDecode = InvokerHelper.invokeMethod(new CodecMetaClassSupportSpecTarget('a&amp;b'), 'decodeBenchmark', null)
            // Same factory still keeps the first cached encoder after concurrent registration.
            codecFactory.encoder = new BenchmarkEncoder('|')
            support.configureCodecMethods(codecFactory, true, targetMetaClasses)
            Object afterReconfigure = InvokerHelper.invokeMethod(new CodecMetaClassSupportSpecTarget('a&b'), 'encodeAsBenchmark', null)

        then:
            concurrentEncode == 'a&amp;b'
            concurrentDecode == 'a&b'
            afterReconfigure == 'a&amp;b'

        cleanup:
            executor.shutdownNow()
    }

    void 'cached codec registration re-adds methods after metaclass replacement'() {
        given:
            CodecMetaClassSupport support = new CodecMetaClassSupport()
            BenchmarkCodecFactory codecFactory = new BenchmarkCodecFactory()

        when:
            support.configureCodecMethods(codecFactory, true, [GrailsMetaClassUtils.getExpandoMetaClass(CodecMetaClassSupportSpecTarget)])
            Object firstResult = InvokerHelper.invokeMethod(new CodecMetaClassSupportSpecTarget('a&b'), 'encodeAsBenchmark', null)
            GroovySystem.metaClassRegistry.removeMetaClass(CodecMetaClassSupportSpecTarget)
            support.configureCodecMethods(codecFactory, true, [GrailsMetaClassUtils.getExpandoMetaClass(CodecMetaClassSupportSpecTarget)])
            Object secondResult = InvokerHelper.invokeMethod(new CodecMetaClassSupportSpecTarget('a&b'), 'encodeAsBenchmark', null)

        then:
            firstResult == 'a&amp;b'
            secondResult == 'a&amp;b'
    }

    void 'cached codec registration keeps distinct codec factories isolated'() {
        given:
            CodecMetaClassSupport support = new CodecMetaClassSupport()
            List<ExpandoMetaClass> targetMetaClasses = [
                GrailsMetaClassUtils.getExpandoMetaClass(CodecMetaClassSupportSpecTarget),
                GrailsMetaClassUtils.getExpandoMetaClass(CodecMetaClassSupportSecondSpecTarget)
            ]

        when:
            support.configureCodecMethods(new BenchmarkCodecFactory('&amp;'), true, targetMetaClasses)
            Object firstResult = InvokerHelper.invokeMethod(new CodecMetaClassSupportSpecTarget('a&b'), 'encodeAsBenchmark', null)
            Object firstDecodedResult = InvokerHelper.invokeMethod(new CodecMetaClassSupportSpecTarget('a&amp;b'), 'decodeBenchmark', null)
            Object firstSecondTargetResult = InvokerHelper.invokeMethod(new CodecMetaClassSupportSecondSpecTarget('a&b'), 'encodeAsBenchmark', null)
            Object firstSecondTargetDecodedResult = InvokerHelper.invokeMethod(new CodecMetaClassSupportSecondSpecTarget('a&amp;b'), 'decodeBenchmark', null)
            support.configureCodecMethods(new BenchmarkCodecFactory('|', '|'), true, targetMetaClasses)
            Object secondResult = InvokerHelper.invokeMethod(new CodecMetaClassSupportSpecTarget('a&b'), 'encodeAsBenchmark', null)
            Object secondDecodedResult = InvokerHelper.invokeMethod(new CodecMetaClassSupportSpecTarget('a&amp;b'), 'decodeBenchmark', null)
            Object secondSecondTargetResult = InvokerHelper.invokeMethod(new CodecMetaClassSupportSecondSpecTarget('a&b'), 'encodeAsBenchmark', null)
            Object secondSecondTargetDecodedResult = InvokerHelper.invokeMethod(new CodecMetaClassSupportSecondSpecTarget('a&amp;b'), 'decodeBenchmark', null)

        then:
            firstResult == 'a&amp;b'
            firstDecodedResult == 'a&b'
            firstSecondTargetResult == 'a&amp;b'
            firstSecondTargetDecodedResult == 'a&b'
            secondResult == 'a|b'
            secondDecodedResult == 'a|b'
            secondSecondTargetResult == 'a|b'
            secondSecondTargetDecodedResult == 'a|b'
    }

    void 'cached codec registration remains correct after unrelated factory churn'() {
        given:
            CodecMetaClassSupport support = new CodecMetaClassSupport()
            List<ExpandoMetaClass> targetMetaClasses = [GrailsMetaClassUtils.getExpandoMetaClass(CodecMetaClassSupportSpecTarget)]
            BenchmarkCodecFactory originalCodecFactory = new BenchmarkCodecFactory()

        when:
            support.configureCodecMethods(originalCodecFactory, true, targetMetaClasses)
            (1..1500).each {
                support.configureCodecMethods(new EncoderOnlyCodecFactory(), true, targetMetaClasses)
            }
            support.configureCodecMethods(originalCodecFactory, true, targetMetaClasses)

        then:
            InvokerHelper.invokeMethod(new CodecMetaClassSupportSpecTarget('a&b'), 'encodeAsBenchmark', null) == 'a&amp;b'
    }

    void 'cached codec registration dispatches through the add meta method extension hook'() {
        given:
            HookTrackingCodecMetaClassSupport support = new HookTrackingCodecMetaClassSupport()
            BenchmarkCodecFactory codecFactory = new BenchmarkCodecFactory()
            List<ExpandoMetaClass> targetMetaClasses = [GrailsMetaClassUtils.getExpandoMetaClass(CodecMetaClassSupportSpecTarget)]

        when:
            support.configureCodecMethods(codecFactory, true, targetMetaClasses)

        then:
            support.registeredMethodNames.contains('encodeAsBenchmark')
            InvokerHelper.invokeMethod(new CodecMetaClassSupportSpecTarget('a&b'), 'encodeAsBenchmark', null) == 'a&amp;b'
    }

    void 'non-cached codec registration keeps development reload behavior'() {
        given:
            CodecMetaClassSupport support = new CodecMetaClassSupport()
            BenchmarkCodecFactory codecFactory = new BenchmarkCodecFactory()
            List<ExpandoMetaClass> targetMetaClasses = [GrailsMetaClassUtils.getExpandoMetaClass(CodecMetaClassSupportSpecTarget)]

        when:
            support.configureCodecMethods(codecFactory, false, targetMetaClasses)
            Object firstResult = InvokerHelper.invokeMethod(new CodecMetaClassSupportSpecTarget('a&b'), 'encodeAsBenchmark', null)
            codecFactory.encoder = new BenchmarkEncoder('|')
            support.configureCodecMethods(codecFactory, false, targetMetaClasses)
            Object secondResult = InvokerHelper.invokeMethod(new CodecMetaClassSupportSpecTarget('a&b'), 'encodeAsBenchmark', null)

        then:
            firstResult == 'a&amp;b'
            secondResult == 'a|b'
    }

    private static class CodecMetaClassSupportSpecTarget {

        private final String value

        CodecMetaClassSupportSpecTarget(String value) {
            this.value = value
        }

        @Override
        String toString() {
            value
        }
    }

    private static class HookTrackingCodecMetaClassSupport extends CodecMetaClassSupport {

        private final List<String> registeredMethodNames = []

        @Override
        protected void addMetaMethod(List<ExpandoMetaClass> targetMetaClasses, String methodName, Closure closure) {
            registeredMethodNames << methodName
            super.addMetaMethod(targetMetaClasses, methodName, closure)
        }
    }

    private static class CodecMetaClassSupportSecondSpecTarget {

        private final String value

        CodecMetaClassSupportSecondSpecTarget(String value) {
            this.value = value
        }

        @Override
        String toString() {
            value
        }
    }

    private static class BenchmarkCodecFactory implements CodecFactory {

        private BenchmarkEncoder encoder
        private final BenchmarkDecoder decoder

        BenchmarkCodecFactory(String ampersandReplacement = '&amp;', String decodedAmpersand = '&') {
            this.encoder = new BenchmarkEncoder(ampersandReplacement)
            this.decoder = new BenchmarkDecoder(decodedAmpersand)
        }

        @Override
        Encoder getEncoder() {
            encoder
        }

        @Override
        Decoder getDecoder() {
            decoder
        }
    }

    private static class EncoderOnlyCodecFactory implements CodecFactory {

        private final BenchmarkEncoder encoder = new BenchmarkEncoder()

        @Override
        Encoder getEncoder() {
            encoder
        }

        @Override
        Decoder getDecoder() {
            null
        }
    }

    private static class BenchmarkEncoder implements Encoder {

        private final CodecIdentifier codecIdentifier = new DefaultCodecIdentifier('Benchmark', 'Bench')
        private final String ampersandReplacement

        BenchmarkEncoder(String ampersandReplacement = '&amp;') {
            this.ampersandReplacement = ampersandReplacement
        }

        @Override
        CodecIdentifier getCodecIdentifier() {
            codecIdentifier
        }

        @Override
        Object encode(Object o) {
            o?.toString()?.replace('&', ampersandReplacement)
        }

        @Override
        void markEncoded(CharSequence string) {
        }

        @Override
        boolean isSafe() {
            true
        }

        @Override
        boolean isApplyToSafelyEncoded() {
            false
        }
    }

    private static class BenchmarkDecoder implements Decoder {

        private final CodecIdentifier codecIdentifier = new DefaultCodecIdentifier('Benchmark', 'Bench')
        private final String decodedAmpersand

        BenchmarkDecoder(String decodedAmpersand = '&') {
            this.decodedAmpersand = decodedAmpersand
        }

        @Override
        CodecIdentifier getCodecIdentifier() {
            codecIdentifier
        }

        @Override
        Object decode(Object o) {
            o?.toString()?.replace('&amp;', decodedAmpersand)
        }
    }
}
