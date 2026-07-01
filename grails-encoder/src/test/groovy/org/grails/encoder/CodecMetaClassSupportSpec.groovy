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

import java.lang.ref.WeakReference
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

    void setup() {
        CodecMetaClassSupport.clearMetaMethodRegistrationState()
    }

    void cleanup() {
        GroovySystem.metaClassRegistry.removeMetaClass(CodecMetaClassSupportSpecTarget)
        CodecMetaClassSupport.clearMetaMethodRegistrationState()
    }

    void 'cached codec registration is idempotent for the same codec factory'() {
        given:
            CodecMetaClassSupport support = new CodecMetaClassSupport()
            BenchmarkCodecFactory codecFactory = new BenchmarkCodecFactory()
            List<ExpandoMetaClass> targetMetaClasses = [GrailsMetaClassUtils.getExpandoMetaClass(CodecMetaClassSupportSpecTarget)]

        when:
            long baselineRegistrationCount = CodecMetaClassSupport.metaMethodRegistrationCount
            support.configureCodecMethods(codecFactory, true, targetMetaClasses)
            long firstRegistrationCount = CodecMetaClassSupport.metaMethodRegistrationCount
            Object firstResult = InvokerHelper.invokeMethod(new CodecMetaClassSupportSpecTarget('a&b'), 'encodeAsBenchmark', null)
            support.configureCodecMethods(codecFactory, true, targetMetaClasses)
            long secondRegistrationCount = CodecMetaClassSupport.metaMethodRegistrationCount
            Object secondResult = InvokerHelper.invokeMethod(new CodecMetaClassSupportSpecTarget('a&b'), 'encodeAsBenchmark', null)

        then:
            firstResult == 'a&amp;b'
            secondResult == 'a&amp;b'
            firstRegistrationCount > baselineRegistrationCount
            secondRegistrationCount == firstRegistrationCount
    }

    void 'cached codec registration is idempotent for decoder and aliases'() {
        given:
            CodecMetaClassSupport support = new CodecMetaClassSupport()
            BenchmarkCodecFactory codecFactory = new BenchmarkCodecFactory()
            List<ExpandoMetaClass> targetMetaClasses = [GrailsMetaClassUtils.getExpandoMetaClass(CodecMetaClassSupportSpecTarget)]

        when:
            support.configureCodecMethods(codecFactory, true, targetMetaClasses)
            long firstRegistrationCount = CodecMetaClassSupport.metaMethodRegistrationCount
            Object encodedAlias = InvokerHelper.invokeMethod(new CodecMetaClassSupportSpecTarget('a&b'), 'encodeAsBench', null)
            Object decoded = InvokerHelper.invokeMethod(new CodecMetaClassSupportSpecTarget('a&amp;b'), 'decodeBenchmark', null)
            Object decodedAlias = InvokerHelper.invokeMethod(new CodecMetaClassSupportSpecTarget('a&amp;b'), 'decodeBench', null)
            support.configureCodecMethods(codecFactory, true, targetMetaClasses)
            long secondRegistrationCount = CodecMetaClassSupport.metaMethodRegistrationCount

        then:
            encodedAlias == 'a&amp;b'
            decoded == 'a&b'
            decodedAlias == 'a&b'
            firstRegistrationCount == 4L
            secondRegistrationCount == firstRegistrationCount
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

        then:
            CodecMetaClassSupport.metaMethodRegistrationCount == 4L

        cleanup:
            executor.shutdownNow()
    }

    void 'cached codec registration re-adds methods after metaclass replacement'() {
        given:
            CodecMetaClassSupport support = new CodecMetaClassSupport()
            BenchmarkCodecFactory codecFactory = new BenchmarkCodecFactory()

        when:
            long baselineRegistrationCount = CodecMetaClassSupport.metaMethodRegistrationCount
            support.configureCodecMethods(codecFactory, true, [GrailsMetaClassUtils.getExpandoMetaClass(CodecMetaClassSupportSpecTarget)])
            long firstRegistrationCount = CodecMetaClassSupport.metaMethodRegistrationCount
            Object firstResult = InvokerHelper.invokeMethod(new CodecMetaClassSupportSpecTarget('a&b'), 'encodeAsBenchmark', null)
            GroovySystem.metaClassRegistry.removeMetaClass(CodecMetaClassSupportSpecTarget)
            support.configureCodecMethods(codecFactory, true, [GrailsMetaClassUtils.getExpandoMetaClass(CodecMetaClassSupportSpecTarget)])
            long secondRegistrationCount = CodecMetaClassSupport.metaMethodRegistrationCount
            Object secondResult = InvokerHelper.invokeMethod(new CodecMetaClassSupportSpecTarget('a&b'), 'encodeAsBenchmark', null)

        then:
            firstResult == 'a&amp;b'
            secondResult == 'a&amp;b'
            firstRegistrationCount > baselineRegistrationCount
            secondRegistrationCount > firstRegistrationCount
    }

    void 'cached codec registration keeps distinct codec factories isolated'() {
        given:
            CodecMetaClassSupport support = new CodecMetaClassSupport()
            List<ExpandoMetaClass> targetMetaClasses = [GrailsMetaClassUtils.getExpandoMetaClass(CodecMetaClassSupportSpecTarget)]

        when:
            long baselineRegistrationCount = CodecMetaClassSupport.metaMethodRegistrationCount
            support.configureCodecMethods(new BenchmarkCodecFactory(), true, targetMetaClasses)
            long firstRegistrationCount = CodecMetaClassSupport.metaMethodRegistrationCount
            Object firstResult = InvokerHelper.invokeMethod(new CodecMetaClassSupportSpecTarget('a&b'), 'encodeAsBenchmark', null)
            support.configureCodecMethods(new BenchmarkCodecFactory(), true, targetMetaClasses)
            long secondRegistrationCount = CodecMetaClassSupport.metaMethodRegistrationCount
            Object secondResult = InvokerHelper.invokeMethod(new CodecMetaClassSupportSpecTarget('a&b'), 'encodeAsBenchmark', null)

        then:
            firstResult == 'a&amp;b'
            secondResult == 'a&amp;b'
            firstRegistrationCount > baselineRegistrationCount
            secondRegistrationCount > firstRegistrationCount
    }

    void 'cached codec registration prunes stale factory registration keys'() {
        given:
            CodecMetaClassSupport support = new CodecMetaClassSupport()
            List<ExpandoMetaClass> targetMetaClasses = [GrailsMetaClassUtils.getExpandoMetaClass(CodecMetaClassSupportSpecTarget)]

        when:
            support.configureCodecMethods(new BenchmarkCodecFactory(), true, targetMetaClasses)
            clearRegisteredCodecFactoryReferences()
            support.configureCodecMethods(new BenchmarkCodecFactory(), true, targetMetaClasses)

        then:
            metaMethodRegistrationKeyCount() == 4
            CodecMetaClassSupport.metaMethodRegistrationCount == 8L
    }

    void 'non-cached codec registration keeps development reload behavior'() {
        given:
            CodecMetaClassSupport support = new CodecMetaClassSupport()
            BenchmarkCodecFactory codecFactory = new BenchmarkCodecFactory()
            List<ExpandoMetaClass> targetMetaClasses = [GrailsMetaClassUtils.getExpandoMetaClass(CodecMetaClassSupportSpecTarget)]

        when:
            long baselineRegistrationCount = CodecMetaClassSupport.metaMethodRegistrationCount
            support.configureCodecMethods(codecFactory, false, targetMetaClasses)
            long firstRegistrationCount = CodecMetaClassSupport.metaMethodRegistrationCount
            support.configureCodecMethods(codecFactory, false, targetMetaClasses)
            long secondRegistrationCount = CodecMetaClassSupport.metaMethodRegistrationCount

        then:
            firstRegistrationCount > baselineRegistrationCount
            secondRegistrationCount > firstRegistrationCount
    }

    private static Set registeredMetaMethodKeys() {
        def field = CodecMetaClassSupport.getDeclaredField('REGISTERED_META_METHODS')
        field.accessible = true
        (Set) field.get(null)
    }

    private static int metaMethodRegistrationKeyCount() {
        CodecMetaClassSupport.metaMethodRegistrationKeyCount
    }

    private static void clearRegisteredCodecFactoryReferences() {
        registeredMetaMethodKeys().each { Object key ->
            def field = key.getClass().getDeclaredField('codecFactoryReference')
            field.accessible = true
            ((WeakReference) field.get(key)).clear()
        }
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

    private static class BenchmarkCodecFactory implements CodecFactory {

        private final BenchmarkEncoder encoder = new BenchmarkEncoder()
        private final BenchmarkDecoder decoder = new BenchmarkDecoder()

        @Override
        Encoder getEncoder() {
            encoder
        }

        @Override
        Decoder getDecoder() {
            decoder
        }
    }

    private static class BenchmarkEncoder implements Encoder {

        private final CodecIdentifier codecIdentifier = new DefaultCodecIdentifier('Benchmark', 'Bench')

        @Override
        CodecIdentifier getCodecIdentifier() {
            codecIdentifier
        }

        @Override
        Object encode(Object o) {
            o?.toString()?.replace('&', '&amp;')
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

        @Override
        CodecIdentifier getCodecIdentifier() {
            codecIdentifier
        }

        @Override
        Object decode(Object o) {
            o?.toString()?.replace('&amp;', '&')
        }
    }
}
