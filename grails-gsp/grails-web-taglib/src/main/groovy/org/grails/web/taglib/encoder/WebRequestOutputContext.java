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

package org.grails.web.taglib.encoder;

import org.grails.web.servlet.mvc.GrailsWebRequest;

/**
 * Created by lari on 02/01/15.
 */
public class WebRequestOutputContext extends WebOutputContextLookup.WebOutputContext {
    private final GrailsWebRequest webRequest;

    public WebRequestOutputContext(GrailsWebRequest webRequest) {
        this.webRequest = webRequest;
    }

    protected GrailsWebRequest lookupWebRequest() {
        return webRequest;
    }
}
