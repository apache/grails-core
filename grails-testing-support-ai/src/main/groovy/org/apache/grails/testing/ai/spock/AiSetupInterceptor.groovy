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
package org.apache.grails.testing.ai.spock

import groovy.transform.CompileStatic
import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation

import org.springframework.context.ApplicationContext
import org.springframework.util.ClassUtils

import grails.testing.ai.AiUnitTest
import org.apache.grails.testing.ai.AiTestBeans
import org.apache.grails.testing.ai.ScriptedChatModel
import org.apache.grails.testing.ai.SimpleVectorStoreBeans

/**
 * Clears the scripted answers, the recorded prompts and the vector store before every feature method, so
 * one feature's script cannot answer another's and its documents cannot turn up in another's search.
 *
 * @since 8.1
 */
@CompileStatic
class AiSetupInterceptor implements IMethodInterceptor {

    @Override
    void intercept(IMethodInvocation invocation) throws Throwable {
        AiUnitTest test = (AiUnitTest) invocation.instance
        ApplicationContext context = test.applicationContext
        for (ScriptedChatModel chatModel : context.getBeansOfType(ScriptedChatModel).values()) {
            chatModel.reset()
        }
        if (ClassUtils.isPresent(AiTestBeans.SIMPLE_VECTOR_STORE, AiSetupInterceptor.classLoader)) {
            SimpleVectorStoreBeans.reset(context)
        }
        invocation.proceed()
    }

}
