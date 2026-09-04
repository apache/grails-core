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

import java.util.concurrent.ConcurrentHashMap

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import org.codehaus.groovy.runtime.GStringImpl
import org.codehaus.groovy.runtime.NullObject

import org.springframework.util.Assert

import grails.util.Environment
import grails.util.GrailsMetaClassUtils

/**
 * Helper methods for Codec metaclass operations.
 *
 * @author Lari Hotari
 * @since 2.3
 */
@CompileStatic
class CodecMetaClassSupport {

    static final Object[] EMPTY_ARGS = []
    static final String ENCODE_AS_PREFIX = 'encodeAs'
    static final String DECODE_PREFIX = 'decode'
    private static final Cache<CodecFactory, Set<MetaMethodRegistrationKey>> REGISTERED_META_METHODS = Caffeine.newBuilder()
            .weakKeys()
            .build()

    /**
     * Adds "encodeAs*" and "decode*" metamethods for given codecClass
     *
     * @param codecClass the codec class
     */
    void configureCodecMethods(CodecFactory codecFactory, boolean cacheLookup = !Environment.getCurrent().isDevelopmentMode(), List<ExpandoMetaClass> targetMetaClasses = resolveDefaultMetaClasses()) {
        Closure<String> encodeMethodNameClosure = { String codecName -> "${ENCODE_AS_PREFIX}${codecName}".toString() }
        Closure<String> decodeMethodNameClosure = { String codecName -> "${DECODE_PREFIX}${codecName}".toString() }

        String codecName = resolveCodecName(codecFactory)
        Assert.hasText(codecName, 'No resolvable codec name')

        String encodeMethodName = encodeMethodNameClosure(codecName)
        String decodeMethodName = decodeMethodNameClosure(codecName)

        Closure encoderClosure
        Closure decoderClosure
        if (!cacheLookup) {
            // Resolve codecs in every call in case of a codec reload
            encoderClosure = {
                ->
                def encoder = codecFactory.getEncoder()
                if (encoder) {
                    return encoder.encode(CodecMetaClassSupport.filterNullObject(delegate))
                }

                // note the call to delegate.getClass() instead of the more groovy delegate.class.
                // this is because the delegate might be a Map, in which case delegate.class doesn't
                // do what we want here...
                throw new MissingMethodException(encodeMethodName, delegate.getClass(), EMPTY_ARGS)
            }

            decoderClosure = {
                ->
                def decoder = codecFactory.getDecoder()
                if (decoder) {
                    return decoder.decode(CodecMetaClassSupport.filterNullObject(delegate))
                }

                // note the call to delegate.getClass() instead of the more groovy delegate.class.
                // this is because the delegate might be a Map, in which case delegate.class doesn't
                // do what we want here...
                throw new MissingMethodException(decodeMethodName, delegate.getClass(), EMPTY_ARGS)
            }
        }
        else {
            // Resolve codec methods once only at startup
            def encoder = codecFactory.getEncoder()
            if (encoder) {
                encoderClosure = { -> encoder.encode(CodecMetaClassSupport.filterNullObject(delegate)) }
            } else {
                encoderClosure = { -> throw new MissingMethodException(encodeMethodName, delegate.getClass(), EMPTY_ARGS) }
            }
            def decoder = codecFactory.getDecoder()
            if (decoder) {
                decoderClosure = { -> decoder.decode(CodecMetaClassSupport.filterNullObject(delegate)) }
            } else {
                decoderClosure = { -> throw new MissingMethodException(decodeMethodName, delegate.getClass(), EMPTY_ARGS) }
            }
        }

        Set<MetaMethodRegistrationKey> registeredMetaMethodKeys = cacheLookup ? registeredMetaMethodKeys(codecFactory) : null
        registerMetaMethod(targetMetaClasses, encodeMethodName, encoderClosure, cacheLookup, registeredMetaMethodKeys)
        if (codecFactory.encoder) {
            addAliasMetaMethods(targetMetaClasses, codecFactory.encoder.codecIdentifier.codecAliases, encodeMethodNameClosure, encoderClosure, cacheLookup, registeredMetaMethodKeys)
        }

        registerMetaMethod(targetMetaClasses, decodeMethodName, decoderClosure, cacheLookup, registeredMetaMethodKeys)
        if (codecFactory.decoder) {
            addAliasMetaMethods(targetMetaClasses, codecFactory.decoder.codecIdentifier.codecAliases, decodeMethodNameClosure, decoderClosure, cacheLookup, registeredMetaMethodKeys)
        }
    }

    /**
     * returns given parameter if it's not a Groovy NullObject (and is not null)
     *
     * The check is made by looking at the Object's class, since NullObject.is & equals give wrong results (Groovy bug?).
     *
     * A NullObject get's passed to the closure in delegate perhaps because of a Groovy bug or feature
     * This happens when a NullObject's MetaMethod is called.
     *
     * @param delegate
     * @return
     */
    private static Object filterNullObject(Object delegate) {
        delegate != null && delegate.getClass() != NullObject ? delegate : null
    }

    private addAliasMetaMethods(List<ExpandoMetaClass> targetMetaClasses, Set<String> aliases, Closure<String> methodNameClosure, Closure methodClosure,
            boolean cacheLookup, Set<MetaMethodRegistrationKey> registeredMetaMethodKeys) {
        aliases?.each { String aliasName ->
            registerMetaMethod(targetMetaClasses, methodNameClosure(aliasName), methodClosure, cacheLookup, registeredMetaMethodKeys)
        }
    }

    private static String resolveCodecName(CodecFactory codecFactory) {
        codecFactory.encoder?.codecIdentifier?.codecName ?: codecFactory.decoder?.codecIdentifier?.codecName
    }

    private static List<ExpandoMetaClass> resolveDefaultMetaClasses() {
        [
            String,
            GStringImpl,
            StringBuffer,
            StringBuilder,
            Object
        ].collect { Class clazz ->
            GrailsMetaClassUtils.getExpandoMetaClass(clazz)
        }
    }

    // The metamethod name is only known at run time, and a GString property name is the one
    // thing static compilation cannot express.
    @CompileDynamic
    protected void addMetaMethod(List<ExpandoMetaClass> targetMetaClasses, String methodName, Closure closure) {
        targetMetaClasses.each { ExpandoMetaClass emc ->
            emc."${methodName}" << closure
        }
    }

    private void registerMetaMethod(List<ExpandoMetaClass> targetMetaClasses, String methodName, Closure closure, boolean cacheLookup,
            Set<MetaMethodRegistrationKey> registeredMetaMethodKeys) {
        if (!cacheLookup) {
            addMetaMethod(targetMetaClasses, methodName, closure)
            return
        }

        synchronized (registeredMetaMethodKeys) {
            List<ExpandoMetaClass> metaClassesToRegister = targetMetaClasses.findAll { ExpandoMetaClass emc ->
                shouldRegisterMetaMethod(emc, methodName, registeredMetaMethodKeys)
            }
            if (metaClassesToRegister) {
                addMetaMethod(metaClassesToRegister, methodName, closure)
            }
        }
    }

    private static boolean shouldRegisterMetaMethod(ExpandoMetaClass emc, String methodName, Set<MetaMethodRegistrationKey> registeredMetaMethodKeys) {
        MetaMethodRegistrationKey key = registrationKey(emc, methodName)
        registeredMetaMethodKeys.add(key) || emc.getMetaMethod(methodName, EMPTY_ARGS) == null
    }

    private static MetaMethodRegistrationKey registrationKey(ExpandoMetaClass emc, String methodName) {
        new MetaMethodRegistrationKey(emc.getTheClass(), methodName)
    }

    private static Set<MetaMethodRegistrationKey> registeredMetaMethodKeys(CodecFactory codecFactory) {
        REGISTERED_META_METHODS.get(codecFactory) { CodecFactory ignored ->
            Collections.newSetFromMap(new ConcurrentHashMap<MetaMethodRegistrationKey, Boolean>())
        }
    }

    @CompileStatic
    @EqualsAndHashCode(includeFields = true)
    private static class MetaMethodRegistrationKey {

        private final Class<?> targetClass
        private final String methodName

        MetaMethodRegistrationKey(Class<?> targetClass, String methodName) {
            this.targetClass = targetClass
            this.methodName = methodName
        }

    }
}
