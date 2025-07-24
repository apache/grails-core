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
package org.grails.encoder.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.beans.factory.InitializingBean;

import grails.util.GrailsNameUtils;
import org.grails.encoder.ChainedDecoder;
import org.grails.encoder.ChainedEncoder;
import org.grails.encoder.ChainedEncoders;
import org.grails.encoder.CodecFactory;
import org.grails.encoder.CodecIdentifierProvider;
import org.grails.encoder.CodecLookup;
import org.grails.encoder.Decoder;
import org.grails.encoder.Encoder;
import org.grails.encoder.StreamingEncoder;

public class BasicCodecLookup implements CodecLookup, InitializingBean {
    private static final String NONE_CODEC_NAME = "none";
    public static final StreamingEncoder NONE_ENCODER = new NoneEncoder();

    protected final ConcurrentMap<String, Encoder> encoders = new ConcurrentHashMap<String, Encoder>();
    protected final ConcurrentMap<String, Decoder> decoders = new ConcurrentHashMap<String, Decoder>();

    public BasicCodecLookup() {
        super();
    }

    public Encoder lookupEncoder(String codecName) {
        return lookupCodec(codecName, encoders, Encoder.class);
    }

    public Decoder lookupDecoder(String codecName) {
        return lookupCodec(codecName, decoders, Decoder.class);
    }

    @SuppressWarnings("unchecked")
    protected <T extends CodecIdentifierProvider> T lookupCodec(String codecName, ConcurrentMap<String, T> map, Class<T> returnType) {
        if (codecName != null && codecName.length() > 0) {
            if (NONE_CODEC_NAME.equalsIgnoreCase(codecName)) {
                if (returnType == Encoder.class) {
                    return (T)NONE_ENCODER;
                }
            } else {
                T resultObject = map.get(codecName);
                if(resultObject == null) {
                    resultObject = createCodec(codecName, map, returnType);
                }
                return resultObject;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    protected <T extends CodecIdentifierProvider> T createCodec(String codecName, ConcurrentMap<String, T> map, Class<T> returnType) {
        if(codecName.indexOf(',') > -1) {
            T createdInstance = createChainedCodecInstance(codecName, map, returnType);
            if(createdInstance != null) {
                createdInstance = putChainedCodecInstance(codecName, map, createdInstance);
            }
            return createdInstance;
        }
        return null;
    }

    protected <T extends CodecIdentifierProvider> T putChainedCodecInstance(String codecName,
            ConcurrentMap<String, T> map, T createdInstance) {
        T previousInstance = map.putIfAbsent(codecName, createdInstance);
        if(previousInstance != null) {
            return previousInstance;
        } else {
            return createdInstance;
        }
    }

    protected <T extends CodecIdentifierProvider> T createChainedCodecInstance(String codecName, ConcurrentMap<String, T> map, Class<T> returnType) {
        String[] codecs=codecName.split(",");
        List<T> codecInstances = new ArrayList<T>(codecs.length);
        for(int i=0;i < codecs.length;i++) {
            T codecInstance = map.get(codecs[i]);
            if(codecInstance != null) {
                codecInstances.add(codecInstance);
            }
        }
        if (returnType == Encoder.class) {
            List<StreamingEncoder> streamingEncoders = ChainedEncoders.toStreamingEncoders((List<Encoder>)codecInstances);
            if(streamingEncoders == null) {
                throw new RuntimeException("ChainedEncoder only supports StreamingEncoder instances. Couldn't build chained encoder for '" + codecName + "'");
            } else {
                return (T)ChainedEncoder.createFor(streamingEncoders);
            }
        } else {
            Collections.reverse(codecInstances);
            return (T)new ChainedDecoder(codecInstances.toArray(new Decoder[codecInstances.size()]));
        }
    }

    protected synchronized <T extends CodecIdentifierProvider> void registerWithNameVaritions(Map<String, T> destinationMap, T target) {
        String name=target.getCodecIdentifier().getCodecName();
        registerVariationsOfName(destinationMap, target, name);
        Set<String> aliases = target.getCodecIdentifier().getCodecAliases();
        if (aliases != null)  {
            for (String alias : aliases) {
                registerVariationsOfName(destinationMap, target, alias);
            }
        }
    }

    protected <T extends CodecIdentifierProvider> void registerVariationsOfName(Map<String, T> destinationMap, T target, String name) {
        Collection<String> nameVariations = createNameVariations(name, target);
        for(String nameVariation : nameVariations) {
            destinationMap.put(nameVariation, target);
        }
    }

    protected Collection<String> createNameVariations(String name, CodecIdentifierProvider target) {
        Set<String> nameVariations = new LinkedHashSet<String>();
        nameVariations.add(name);
        nameVariations.add(name.toLowerCase());
        nameVariations.add(name.toUpperCase());
        nameVariations.add(GrailsNameUtils.getPropertyNameRepresentation(name));
        return nameVariations;
    }

    public void registerCodecFactory(CodecFactory codecFactory) {
        Encoder encoder=codecFactory.getEncoder();
        if (encoder != null) {
            registerEncoder(encoder);
        }
        Decoder decoder=codecFactory.getDecoder();
        if (decoder != null) {
            registerDecoder(decoder);
        }
    }

    public void registerDecoder(Decoder decoder) {
        registerWithNameVaritions(decoders, decoder);
    }

    public void registerEncoder(Encoder encoder) {
        registerWithNameVaritions(encoders, encoder);
    }

    public void reInitialize() {
        encoders.clear(); ;
        decoders.clear();
        registerCodecs();
    }

    protected void registerCodecs() {

    }

    public void afterPropertiesSet() throws Exception {
        reInitialize();
    }
}
