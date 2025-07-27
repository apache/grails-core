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
package org.grails.encoder.impl

import groovy.transform.CompileStatic
import org.codehaus.groovy.runtime.GStringImpl

import grails.util.GrailsMetaClassUtils
import org.grails.encoder.CodecFactory
import org.grails.encoder.CodecMetaClassSupport

@CompileStatic
class StandaloneCodecLookup extends BasicCodecLookup {
    boolean registerMetaMethods = true
    boolean cacheLookupsInMetaMethods = true
    Collection<Class<? extends Object>> targetClassesForMetaMethods = [
        String,
        GStringImpl,
        StringBuffer,
        StringBuilder,
        Object
    ]
    Collection<Class<? extends CodecFactory>> codecFactoryClasses = [
            XMLCodecFactory,
            HTMLCodecFactory,
            JSONCodecFactory,
            JavaScriptCodec,
            HTMLJSCodec,
            URLCodecFactory,
            RawCodec
        ]

    @Override
    protected void registerCodecs() {
        codecFactoryClasses.each { Class clazz ->
            registerCodecFactory((CodecFactory)clazz.getDeclaredConstructor().newInstance())
        }
    }

    @Override
    void registerCodecFactory(CodecFactory codecFactory) {
        super.registerCodecFactory(codecFactory)
        registerMetaMethods(codecFactory)
    }

    protected registerMetaMethods(CodecFactory codecFactory) {
        if(registerMetaMethods && targetClassesForMetaMethods) {
            new CodecMetaClassSupport().configureCodecMethods(codecFactory, cacheLookupsInMetaMethods, resolveMetaClasses())
        }
    }

    protected List<ExpandoMetaClass> resolveMetaClasses() {
        targetClassesForMetaMethods.collect {
            Class clazz ->
            GrailsMetaClassUtils.getExpandoMetaClass(clazz)
        }
    }
}
