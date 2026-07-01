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

import java.util.concurrent.atomic.AtomicInteger

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import groovy.lang.MetaClassRegistryChangeEventListener
import org.codehaus.groovy.runtime.InvokerHelper

import grails.util.GrailsMetaClassUtils

@CompileStatic
class CodecMetaClassBenchmark {

    private static final int REGISTRATION_ITERATIONS = Integer.getInteger('grails.codec.benchmark.registrationIterations', 10_000)
    private static final int ENCODE_ITERATIONS = Integer.getInteger('grails.codec.benchmark.encodeIterations', 1_000_000)
    private static final int ENCODE_WARMUP_ITERATIONS = Integer.getInteger('grails.codec.benchmark.encodeWarmupIterations', 100_000)
    private static final boolean NEW_FACTORY_EACH_REGISTRATION = Boolean.getBoolean('grails.codec.benchmark.newFactoryEachRegistration')

    static void main(String[] args) {
        GroovySystem.metaClassRegistry.removeMetaClass(CodecBenchmarkTarget)
        CodecMetaClassSupport support = new CodecMetaClassSupport()
        BenchmarkCodecFactory codecFactory = new BenchmarkCodecFactory()
        List<ExpandoMetaClass> targetMetaClasses = [GrailsMetaClassUtils.getExpandoMetaClass(CodecBenchmarkTarget)]
        AtomicInteger changeEvents = new AtomicInteger()
        MetaClassRegistryChangeEventListener listener = { changeEvents.incrementAndGet() } as MetaClassRegistryChangeEventListener
        GroovySystem.metaClassRegistry.addMetaClassRegistryChangeEventListener(listener)
        try {
            long registrationStart = System.nanoTime()
            for (int i = 0; i < REGISTRATION_ITERATIONS; i++) {
                CodecFactory registrationFactory = NEW_FACTORY_EACH_REGISTRATION ? new BenchmarkCodecFactory() : codecFactory
                support.configureCodecMethods(registrationFactory, true, targetMetaClasses)
            }
            long registrationNanos = System.nanoTime() - registrationStart

            CodecBenchmarkTarget target = new CodecBenchmarkTarget('a&b<c>d')
            for (int i = 0; i < ENCODE_WARMUP_ITERATIONS; i++) {
                encode(target)
            }
            long encodeStart = System.nanoTime()
            Object encoded = null
            for (int i = 0; i < ENCODE_ITERATIONS; i++) {
                encoded = encode(target)
            }
            long encodeNanos = System.nanoTime() - encodeStart

            println "registrationIterations=${REGISTRATION_ITERATIONS}"
            println "newFactoryEachRegistration=${NEW_FACTORY_EACH_REGISTRATION}"
            println "registrationNanos=${registrationNanos}"
            println "registrationNanosPerOp=${registrationNanos / REGISTRATION_ITERATIONS}"
            println "metaClassChangeEvents=${changeEvents.get()}"
            println "codecMetaMethodRegistrations=${codecMetaMethodRegistrations()}"
            println "encodeAsBenchmarkMetaMethods=${countEncodeAsBenchmarkMetaMethods(targetMetaClasses)}"
            println "encodeIterations=${ENCODE_ITERATIONS}"
            println "encodeNanos=${encodeNanos}"
            println "encodeNanosPerOp=${encodeNanos / ENCODE_ITERATIONS}"
            println "lastEncoded=${encoded}"
        } finally {
            GroovySystem.metaClassRegistry.removeMetaClassRegistryChangeEventListener(listener)
            GroovySystem.metaClassRegistry.removeMetaClass(CodecBenchmarkTarget)
        }
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    private static Object encode(CodecBenchmarkTarget target) {
        InvokerHelper.invokeMethod(target, 'encodeAsBenchmark', null)
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    private static long codecMetaMethodRegistrations() {
        try {
            CodecMetaClassSupport.getMetaMethodRegistrationCount()
        } catch (MissingMethodException ignored) {
            -1L
        }
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    private static int countEncodeAsBenchmarkMetaMethods(List<ExpandoMetaClass> targetMetaClasses) {
        targetMetaClasses.sum { ExpandoMetaClass emc ->
            emc.metaMethods.count { it.name == 'encodeAsBenchmark' }
        } as int
    }

    private static class CodecBenchmarkTarget {

        private final String value

        CodecBenchmarkTarget(String value) {
            this.value = value
        }

        @Override
        String toString() {
            value
        }
    }

    private static class BenchmarkCodecFactory implements CodecFactory {

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

        private final CodecIdentifier codecIdentifier = new DefaultCodecIdentifier('Benchmark')

        @Override
        CodecIdentifier getCodecIdentifier() {
            codecIdentifier
        }

        @Override
        Object encode(Object o) {
            o?.toString()?.replace('&', '&amp;')?.replace('<', '&lt;')?.replace('>', '&gt;')
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
}
