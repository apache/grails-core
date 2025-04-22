/*
 * Copyright 2008 the original author or authors.
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
package org.grails.plugins.testing;

/**
 * Refer to the groovydoc of {@link GrailsMockHttpServletResponse} for further information.
 *
 * @author Graeme Rocher
 * @since 2.1
 */
public class GrailsMockHttpServletResponse extends AbstractGrailsMockHttpServletResponse{
    
    @Override
    public void setForwardedUrl(final String forwardedUrl) {
        String strippedUrl = forwardedUrl;
        if(strippedUrl != null) {
            if(strippedUrl.startsWith("/grails/")) {
                // Strip off /grails, leave the second /
                strippedUrl = strippedUrl.substring(7);
            }
            if(strippedUrl.endsWith(".dispatch")) {
                strippedUrl = strippedUrl.substring(0, strippedUrl.length() - 9);
            }
        }
        super.setForwardedUrl(strippedUrl);
    }
}
