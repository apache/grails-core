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

import org.springframework.web.util.WebUtils

class ErrorPageController {

    /**
     * Hands the container's error to the view, as an application's error page commonly does. The model
     * is exposed as request attributes, so the error becomes the {@code exception} request attribute.
     */
    def handle() {
        render(view: 'handle', model: [exception: request.getAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE)])
    }

    def actionError() {
        throw new ExceptionInInitializerError(new IllegalStateException('Simulated static initializer failure'))
    }
}
