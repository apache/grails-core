/*
 * Copyright 2015-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.grails.web.taglib.encoder;

import grails.core.GrailsApplication;
import org.grails.encoder.EncodingStateRegistry;
import org.grails.taglib.AbstractTemplateVariableBinding;
import org.grails.taglib.TemplateVariableBinding;
import org.grails.taglib.encoder.OutputContext;
import org.grails.taglib.encoder.OutputContextLookup;
import org.grails.taglib.encoder.OutputEncodingStack;
import org.grails.web.servlet.WrappedResponseHolder;
import org.grails.web.servlet.mvc.GrailsWebRequest;
import org.grails.web.taglib.WebRequestTemplateVariableBinding;
import org.grails.web.util.GrailsApplicationAttributes;
import org.grails.web.util.WebUtils;
import org.springframework.core.Ordered;
import org.springframework.web.context.request.RequestAttributes;

import jakarta.servlet.http.HttpServletResponse;
import java.io.Writer;

public class WebOutputContextLookup implements OutputContextLookup, Ordered {
    private static final WebOutputContext webOutputContext = new WebOutputContext();
    static final String ATTRIBUTE_NAME_OUTPUT_STACK="org.grails.web.encoder.OUTPUT_ENCODING_STACK";

    @Override
    public OutputContext lookupOutputContext() {
        return webOutputContext;
    }

    @Override
    public int getOrder() {
        return 0;
    }

    static class WebOutputContext implements OutputContext {
        WebOutputContext() {

        }

        @Override
        public EncodingStateRegistry getEncodingStateRegistry() {
            GrailsWebRequest grailsWebRequest = lookupWebRequest();
            if(grailsWebRequest != null)
                return grailsWebRequest.getEncodingStateRegistry();
            return null;
        }

        @Override
        public void setCurrentOutputEncodingStack(OutputEncodingStack outputEncodingStack) {
            GrailsWebRequest grailsWebRequest = lookupWebRequest();
            if(grailsWebRequest != null)
                grailsWebRequest.setAttribute(ATTRIBUTE_NAME_OUTPUT_STACK, outputEncodingStack, RequestAttributes.SCOPE_REQUEST);
        }

        @Override
        public OutputEncodingStack getCurrentOutputEncodingStack() {
            GrailsWebRequest grailsWebRequest = lookupWebRequest();
            if(grailsWebRequest != null) {
                return (OutputEncodingStack) grailsWebRequest.getAttribute(ATTRIBUTE_NAME_OUTPUT_STACK, RequestAttributes.SCOPE_REQUEST);
            }
            return null;
        }

        @Override
        public Writer getCurrentWriter() {
            return lookupWebRequest().getOut();
        }

        @Override
        public void setCurrentWriter(Writer currentWriter) {
            GrailsWebRequest grailsWebRequest = lookupWebRequest();
            if(grailsWebRequest != null) {
                grailsWebRequest.setOut(currentWriter);
            }
        }

        @Override
        public AbstractTemplateVariableBinding createAndRegisterRootBinding() {
            GrailsWebRequest webRequest = lookupWebRequest();
            AbstractTemplateVariableBinding binding = webRequest != null ? new WebRequestTemplateVariableBinding(webRequest) : new TemplateVariableBinding();
            setBinding(binding);
            return binding;
        }

        @Override
        public AbstractTemplateVariableBinding getBinding() {
            GrailsWebRequest grailsWebRequest = lookupWebRequest();
            if(grailsWebRequest == null) {
                return null;
            }
            return (AbstractTemplateVariableBinding) grailsWebRequest.getAttribute(GrailsApplicationAttributes.PAGE_SCOPE, RequestAttributes.SCOPE_REQUEST);
        }

        @Override
        public void setBinding(AbstractTemplateVariableBinding binding) {
            GrailsWebRequest grailsWebRequest = lookupWebRequest();
            if(grailsWebRequest != null) {
                grailsWebRequest.setAttribute(GrailsApplicationAttributes.PAGE_SCOPE, binding, RequestAttributes.SCOPE_REQUEST);
            }
        }

        @Override
        public GrailsApplication getGrailsApplication() {
            GrailsWebRequest grailsWebRequest = lookupWebRequest();
            if(grailsWebRequest != null)
                return grailsWebRequest.getAttributes().getGrailsApplication();
            return null;
        }

        @Override
        public void setContentType(String contentType) {
            HttpServletResponse httpServletResponse = lookupResponse();
            if(httpServletResponse != null) {
                httpServletResponse.setContentType(contentType);
            }
        }

        @Override
        public boolean isContentTypeAlreadySet() {
            GrailsWebRequest webRequest = lookupWebRequest();
            HttpServletResponse response = webRequest.getResponse();
            return response.isCommitted() || (response.getContentType() != null && webRequest.getRequest().getAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE) == null);
        }

        protected GrailsWebRequest lookupWebRequest() {
            return GrailsWebRequest.lookup();
        }

        protected HttpServletResponse lookupResponse() {
            HttpServletResponse wrapped = WrappedResponseHolder.getWrappedResponse();
            if(wrapped != null) {
                return wrapped;
            }
            else {
                GrailsWebRequest grailsWebRequest = lookupWebRequest();
                if(grailsWebRequest != null) {
                    return grailsWebRequest.getCurrentResponse();
                }
            }
            return null;
        }
    }
}
