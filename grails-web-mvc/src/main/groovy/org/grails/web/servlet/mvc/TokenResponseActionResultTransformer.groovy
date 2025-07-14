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
package org.grails.web.servlet.mvc

import grails.web.mvc.FlashScope
import groovy.transform.CompileStatic
import org.grails.web.servlet.mvc.exceptions.ControllerExecutionException
import org.grails.web.util.WebUtils

/**
 * An {@link ActionResultTransformer} that adds support for the "Synchronizer Token Pattern"
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
class TokenResponseActionResultTransformer implements ActionResultTransformer {

    @Override
    def transformActionResult(GrailsWebRequest webRequest, String viewName, Object actionResult) {
        def request = webRequest.request
        def response = webRequest.response
        TokenResponseHandler handler = (TokenResponseHandler) request.getAttribute(TokenResponseHandler.KEY);
        if (handler != null && !handler.wasInvoked() && handler.wasInvalidToken()) {
            String uri = (String) request.getAttribute(SynchronizerTokensHolder.TOKEN_URI);
            if (uri == null) {
                uri = WebUtils.getForwardURI(request);
            }
            try {
                FlashScope flashScope = webRequest.getFlashScope();
                flashScope.put(TokenResponseHandler.INVALID_TOKEN_ATTRIBUTE, request.getParameter(SynchronizerTokensHolder.TOKEN_KEY));
                response.sendRedirect(uri);
                return null;
            }
            catch (IOException e) {
                throw new ControllerExecutionException("I/O error sending redirect to URI: " + uri, e);
            }
        }
        return actionResult
    }
}
