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

package undertowapp

import groovy.transform.CompileStatic

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.context.request.async.DeferredResult
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.view.InternalResourceView

/**
 * Spring MVC handlers beside the controllers of the application, one of which an asynchronous dispatch forwards to.
 */
@Controller
@CompileStatic
class SpringMvcHandlers {

    @GetMapping('/spring/forwardedToViewAsynchronously')
    DeferredResult<ModelAndView> forwardedToViewAsynchronously() {
        forwardAsynchronously('/spring/answeredByView')
    }

    @GetMapping('/spring/forwardedToControllerAsynchronously')
    DeferredResult<ModelAndView> forwardedToControllerAsynchronously() {
        forwardAsynchronously('/spring/answeredByController')
    }

    @GetMapping('/spring/answeredByView')
    String answeredByView() {
        throw new IllegalStateException('answered by the error view')
    }

    @GetMapping('/spring/answeredByController')
    String answeredByController() {
        throw new UnsupportedOperationException('answered by the error controller')
    }

    private static DeferredResult<ModelAndView> forwardAsynchronously(String path) {
        DeferredResult<ModelAndView> result = new DeferredResult<>()
        result.result = new ModelAndView(new InternalResourceView(path))
        result
    }
}
