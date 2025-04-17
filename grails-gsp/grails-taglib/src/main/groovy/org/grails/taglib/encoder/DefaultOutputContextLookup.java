/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.grails.taglib.encoder;

import grails.core.GrailsApplication;
import grails.util.Holders;
import org.grails.encoder.DefaultEncodingStateRegistry;
import org.grails.encoder.EncodingStateRegistry;
import org.grails.encoder.EncodingStateRegistryLookup;
import org.grails.encoder.EncodingStateRegistryLookupHolder;
import org.grails.taglib.AbstractTemplateVariableBinding;
import org.grails.taglib.TemplateVariableBinding;
import org.springframework.core.Ordered;

import java.io.Writer;

public class DefaultOutputContextLookup implements OutputContextLookup, EncodingStateRegistryLookup, Ordered {
    private ThreadLocal<OutputContext> outputContextThreadLocal = new ThreadLocal<OutputContext>(){
        @Override
        protected OutputContext initialValue() {
            return new DefaultOutputContext();
        }
    };

    @Override
    public OutputContext lookupOutputContext() {
        if(EncodingStateRegistryLookupHolder.getEncodingStateRegistryLookup()==null) {
            // TODO: improve EncodingStateRegistry solution so that global state doesn't have to be used
            EncodingStateRegistryLookupHolder.setEncodingStateRegistryLookup(this);
        }
        return outputContextThreadLocal.get();
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public EncodingStateRegistry lookup() {
        return lookupOutputContext().getEncodingStateRegistry();
    }

    public DefaultOutputContextLookup() {

    }

    public static class DefaultOutputContext implements OutputContext {
        private OutputEncodingStack outputEncodingStack;
        private Writer currentWriter;
        private AbstractTemplateVariableBinding binding;
        private EncodingStateRegistry encodingStateRegistry = new DefaultEncodingStateRegistry();

        public DefaultOutputContext() {

        }

        @Override
        public EncodingStateRegistry getEncodingStateRegistry() {
            return encodingStateRegistry;
        }

        @Override
        public void setCurrentOutputEncodingStack(OutputEncodingStack outputEncodingStack) {
            this.outputEncodingStack = outputEncodingStack;
        }

        @Override
        public OutputEncodingStack getCurrentOutputEncodingStack() {
            return outputEncodingStack;
        }

        @Override
        public Writer getCurrentWriter() {
            return currentWriter;
        }

        @Override
        public void setCurrentWriter(Writer currentWriter) {
            this.currentWriter = currentWriter;
        }

        @Override
        public AbstractTemplateVariableBinding createAndRegisterRootBinding() {
            binding = new TemplateVariableBinding();
            return binding;
        }

        @Override
        public AbstractTemplateVariableBinding getBinding() {
            return binding;
        }

        @Override
        public void setBinding(AbstractTemplateVariableBinding binding) {
            this.binding = binding;
        }

        @Override
        public GrailsApplication getGrailsApplication() {
            return Holders.findApplication();
        }

        @Override
        public void setContentType(String contentType) {
            // no-op
        }

        @Override
        public boolean isContentTypeAlreadySet() {
            return true;
        }
    }
}
