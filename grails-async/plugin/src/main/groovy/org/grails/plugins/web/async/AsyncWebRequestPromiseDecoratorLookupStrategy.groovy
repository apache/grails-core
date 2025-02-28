/*
 * Copyright 2013 the original author or authors.
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
package org.grails.plugins.web.async

import groovy.transform.CompileStatic
import org.grails.web.servlet.mvc.GrailsWebRequest
import grails.async.decorator.PromiseDecorator
import grails.async.decorator.PromiseDecoratorLookupStrategy

/**
 * A promise decorated lookup strategy that binds a WebRequest to the promise thread
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@CompileStatic
class AsyncWebRequestPromiseDecoratorLookupStrategy implements PromiseDecoratorLookupStrategy {
    @Override
    List<PromiseDecorator> findDecorators() {
        final webRequest = GrailsWebRequest.lookup()
        if (webRequest) {
            List<PromiseDecorator> decorators = []
            decorators.add(new AsyncWebRequestPromiseDecorator(webRequest))
            return decorators
        }
        return Collections.emptyList()
    }
}
