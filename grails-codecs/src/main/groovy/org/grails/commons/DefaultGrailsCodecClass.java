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
package org.grails.commons;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import groovy.lang.Closure;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.core.Ordered;
import org.springframework.util.ReflectionUtils;

import org.grails.core.AbstractInjectableGrailsClass;
import org.grails.encoder.CodecFactory;
import org.grails.encoder.CodecIdentifier;
import org.grails.encoder.CodecMetaClassSupport;
import org.grails.encoder.Decoder;
import org.grails.encoder.DefaultCodecIdentifier;
import org.grails.encoder.Encodeable;
import org.grails.encoder.EncodedAppender;
import org.grails.encoder.Encoder;
import org.grails.encoder.EncodingState;
import org.grails.encoder.EncodingStateRegistry;
import org.grails.encoder.EncodingStateRegistryLookup;
import org.grails.encoder.EncodingStateRegistryLookupHolder;
import org.grails.encoder.StreamingEncoder;

/**
 * @author Jeff Brown
 * @since 0.4
 */
public class DefaultGrailsCodecClass extends AbstractInjectableGrailsClass implements InitializingBean, GrailsCodecClass, Ordered {
    public static final String CODEC = CodecArtefactHandler.TYPE;
    private Encoder encoder;
    private Decoder decoder;
    private static int instantionCounter=0;
    private int order = 100 + instantionCounter++;
    private boolean initialized = false;

    public DefaultGrailsCodecClass(Class<?> clazz) {
        super(clazz, CODEC);
    }

    public void afterPropertiesSet() {
        initializeCodec();
    }

    private void initializeCodec() {
        if (initialized) return;
        initialized = true;
        Integer orderSetting = getStaticPropertyValue("order", Integer.class);
        if (orderSetting != null) {
            order = orderSetting;
        }
        Object instance = getReferenceInstance();
        if (Encoder.class.isAssignableFrom(getClazz())) {
            encoder = (Encoder) instance;
            encoder = (Encoder)autowireCodecBean(encoder);
            if (encoder instanceof Ordered) {
                order = ((Ordered)encoder).getOrder();
            }
        }
        if (Decoder.class.isAssignableFrom(getClazz())) {
            decoder = (Decoder) instance;
            decoder = (Decoder)autowireCodecBean(decoder);
            if (decoder instanceof Ordered) {
                order = ((Ordered)decoder).getOrder();
            }
        }
        if (encoder==null && decoder==null) {
            CodecFactory codecFactory=null;
            if (CodecFactory.class.isAssignableFrom(getClazz())) {
                codecFactory=(CodecFactory) instance;
                codecFactory=(CodecFactory)autowireCodecBean(codecFactory);
            }
            if (codecFactory==null) {
                codecFactory=getStaticPropertyValue("codecFactory", CodecFactory.class);
                codecFactory=(CodecFactory)autowireCodecBean(codecFactory);
            }
            if (codecFactory==null) {
                codecFactory=new ClosureCodecFactory(instance);
            }
            encoder=codecFactory.getEncoder();
            decoder=codecFactory.getDecoder();
            if (codecFactory instanceof Ordered) {
                order = ((Ordered)codecFactory).getOrder();
            }
        }
        if (encoder != null) {
            if (encoder instanceof StreamingEncoder) {
                encoder=new StreamingStateAwareEncoderWrapper((StreamingEncoder)encoder);
            } else {
                encoder=new StateAwareEncoderWrapper(encoder);
            }
        }
    }

    protected Object autowireCodecBean(Object existingBean) {
        if (existingBean != null && grailsApplication != null && grailsApplication.getMainContext() != null) {
            AutowireCapableBeanFactory beanFactory = grailsApplication.getMainContext().getAutowireCapableBeanFactory();
            beanFactory.autowireBeanProperties(
                    existingBean, AutowireCapableBeanFactory.AUTOWIRE_BY_NAME, false);
            existingBean = beanFactory.initializeBean(existingBean, "codec");
        }
        return existingBean;
    }

    private class ClosureCodecFactory implements CodecFactory {
        private Encoder encoder;
        private Decoder decoder;
        private final Object codecInstance;

        ClosureCodecFactory(Object codecInstance) {
            this.codecInstance = codecInstance;
            Closure<Object> encoderClosure = getMethodOrClosureMethod(getClazz(), "encode");
            if (encoderClosure != null) {
                encoder=new ClosureEncoder(getName(), encoderClosure);
            }
            Closure<Object> decoderClosure = getMethodOrClosureMethod(getClazz(), "decode");
            if (decoderClosure != null) {
                decoder=new ClosureDecoder(getName(), decoderClosure);
            }
        }

        public Encoder getEncoder() {
            return encoder;
        }

        public Decoder getDecoder() {
            return decoder;
        }

        private Closure<Object> getMethodOrClosureMethod(Class<?> clazz, String methodName) {
            @SuppressWarnings("unchecked")
            Closure<Object> closure = getStaticPropertyValue(methodName, Closure.class);
            if (closure == null) {
                Method method = ReflectionUtils.findMethod(clazz, methodName, (Class<?>[])null);
                if (method != null) {
                    Object owner;
                    if (Modifier.isStatic(method.getModifiers())) {
                        owner=clazz;
                    } else {
                        owner=codecInstance;
                    }
                    return new MethodCallingClosure(owner, method);
                }
                return null;
            } else {
                return closure;
            }
        }
    }

    private static class ClosureDecoder implements Decoder {
        private CodecIdentifier codecIdentifier;
        private Closure<Object> closure;

        public ClosureDecoder(String codecName, Closure<Object> closure) {
            this.codecIdentifier=new DefaultCodecIdentifier(codecName);
            this.closure=closure;
        }

        public CodecIdentifier getCodecIdentifier() {
            return codecIdentifier;
        }

        public Object decode(Object o) {
            return closure.call(o);
        }
    }

    private static class StateAwareEncoderWrapper implements Encoder {
        private Encoder delegate;

        public StateAwareEncoderWrapper(Encoder delegate) {
            this.delegate=delegate;
        }

        public CodecIdentifier getCodecIdentifier() {
            return delegate.getCodecIdentifier();
        }

        public Object encode(Object target) {
            if (target instanceof Encodeable) {
                return ((Encodeable)target).encode(this);
            }

            EncodingStateRegistry encodingState=lookupEncodingState();
            if (encodingState != null && target instanceof CharSequence) {
                if (!encodingState.shouldEncodeWith(this, (CharSequence)target)) {
                    return target;
                }
            }
            Object encoded = delegate.encode(target);
            if (encodingState != null && encoded instanceof CharSequence)
                encodingState.registerEncodedWith(this, (CharSequence)encoded);
            return encoded;
        }

        protected EncodingStateRegistry lookupEncodingState() {
            EncodingStateRegistryLookup encodingStateRegistryLookup = EncodingStateRegistryLookupHolder.getEncodingStateRegistryLookup();
            return encodingStateRegistryLookup != null ? encodingStateRegistryLookup.lookup() : null;
        }

        public void markEncoded(CharSequence string) {
            EncodingStateRegistry encodingState=lookupEncodingState();
            if (encodingState != null) {
                encodingState.registerEncodedWith(this, string);
            }
        }

        public boolean isSafe() {
            return delegate.isSafe();
        }

        public boolean isApplyToSafelyEncoded() {
            return delegate.isApplyToSafelyEncoded();
        }
    }

    private static class StreamingStateAwareEncoderWrapper extends StateAwareEncoderWrapper implements StreamingEncoder {
        private StreamingEncoder delegate;
        public StreamingStateAwareEncoderWrapper(StreamingEncoder delegate) {
            super(delegate);
            this.delegate=delegate;
        }
        public void encodeToStream(Encoder thisInstance, CharSequence source, int offset, int len, EncodedAppender appender,
                EncodingState encodingState) throws IOException {
            delegate.encodeToStream(this, source, offset, len, appender, encodingState);
        }
    }

    private static class ClosureEncoder implements Encoder {
        private CodecIdentifier codecIdentifier;
        private Closure<Object> closure;

        public ClosureEncoder(String codecName, Closure<Object> closure) {
            this.codecIdentifier=new DefaultCodecIdentifier(codecName);
            this.closure=closure;
        }

        public CodecIdentifier getCodecIdentifier() {
            return codecIdentifier;
        }

        public Object encode(Object target) {
            if (target==null) return null;
            return closure.call(target);
        }

        public void markEncoded(CharSequence string) {

        }

        public boolean isSafe() {
            return false;
        }

        public boolean isApplyToSafelyEncoded() {
            return true;
        }
    }

    private static class MethodCallingClosure extends Closure<Object> {
        private static final long serialVersionUID = 1L;
        private Method method;

        public MethodCallingClosure(Object owner, Method method) {
            super(owner);
            maximumNumberOfParameters = 1;
            parameterTypes = new Class[]{Object.class};
            this.method=method;
        }

        protected Object callMethod(Object argument) {
            return ReflectionUtils.invokeMethod(method, !Modifier.isStatic(method.getModifiers()) ? getOwner() : null, argument);
        }

        @Override
        public Object call(Object... args) {
            return doCall(args);
        }

        protected Object doCall(Object[] args) {
            Object target=null;
            if (args != null && args.length > 0)
                target=args[0];
            if (target==null) {
                return null;
            }
            return callMethod(target);
        }
    }

    public Encoder getEncoder() {
        return encoder;
    }

    public Decoder getDecoder() {
        return decoder;
    }

    public void configureCodecMethods() {
        // for compatibility. Not everything (especially unit tests written by existing Grails applications) call afterPropertiesSet(), but everything calls
        // configureCodecMethods() at least once
        initializeCodec();

        new CodecMetaClassSupport().configureCodecMethods(this);
    }

    public int getOrder() {
        return order;
    }
}
