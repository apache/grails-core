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
package org.grails.taglib.encoder

import groovy.transform.CompileStatic

import org.springframework.core.Ordered

import grails.core.GrailsApplication
import grails.util.Holders
import org.grails.encoder.DefaultEncodingStateRegistry
import org.grails.encoder.EncodingStateRegistry
import org.grails.encoder.EncodingStateRegistryLookup
import org.grails.encoder.EncodingStateRegistryLookupHolder
import org.grails.taglib.AbstractTemplateVariableBinding
import org.grails.taglib.TemplateVariableBinding

@CompileStatic
class DefaultOutputContextLookup implements OutputContextLookup, EncodingStateRegistryLookup, Ordered {

    private ThreadLocal<OutputContext> outputContextThreadLocal = new ThreadLocal<OutputContext>() {
        @Override
        protected OutputContext initialValue() {
            return new DefaultOutputContext()
        }
    }

    @Override
    OutputContext lookupOutputContext() {
        if (EncodingStateRegistryLookupHolder.getEncodingStateRegistryLookup() == null) {
            // TODO: improve EncodingStateRegistry solution so that global state doesn't have to be used
            EncodingStateRegistryLookupHolder.setEncodingStateRegistryLookup(this)
        }
        return outputContextThreadLocal.get()
    }

    @Override
    int getOrder() {
        return Ordered.LOWEST_PRECEDENCE
    }

    @Override
    EncodingStateRegistry lookup() {
        return lookupOutputContext().getEncodingStateRegistry()
    }

    static class DefaultOutputContext implements OutputContext {
        private OutputEncodingStack outputEncodingStack
        private Writer currentWriter
        private AbstractTemplateVariableBinding binding
        private EncodingStateRegistry encodingStateRegistry = new DefaultEncodingStateRegistry()

        @Override
        EncodingStateRegistry getEncodingStateRegistry() {
            return encodingStateRegistry
        }

        @Override
        void setCurrentOutputEncodingStack(OutputEncodingStack outputEncodingStack) {
            this.outputEncodingStack = outputEncodingStack
        }

        @Override
        OutputEncodingStack getCurrentOutputEncodingStack() {
            return outputEncodingStack
        }

        @Override
        Writer getCurrentWriter() {
            return currentWriter
        }

        @Override
        void setCurrentWriter(Writer currentWriter) {
            this.currentWriter = currentWriter
        }

        @Override
        AbstractTemplateVariableBinding createAndRegisterRootBinding() {
            binding = new TemplateVariableBinding()
            return binding
        }

        @Override
        AbstractTemplateVariableBinding getBinding() {
            return binding
        }

        @Override
        void setBinding(AbstractTemplateVariableBinding binding) {
            this.binding = binding
        }

        @Override
        GrailsApplication getGrailsApplication() {
            return Holders.findApplication()
        }

        @Override
        void setContentType(String contentType) {
            // no-op
        }

        @Override
        boolean isContentTypeAlreadySet() {
            return true
        }
    }
}
