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
package functionaltests.requestpath

import grails.converters.JSON

/**
 * Reports what one request looked like to each part of the request path, so that a single round trip through
 * a real filter chain, dispatcher, mapping and controller can be asserted end to end.
 *
 * <p>Mapped as a {@code resources} block, so the member URL carries the PUT, PATCH and DELETE routes and -
 * while the hidden HTTP method filter is disabled - the POST route generated alongside them.</p>
 */
class RequestPathController {

    static responseFormats = ['json']

    // allowedMethods reads the effective method, so a form POST naming PUT is admitted as a PUT. POST is
    // allowed alongside it because the member URL answers a bare POST at update, which is what #9926 asked
    // for; delete and patch are reachable only as themselves.
    static allowedMethods = [update: ['PUT', 'POST'], patch: 'PATCH', delete: 'DELETE']

    def index() {
        render(report() as JSON)
    }

    def update(RequestPathCommand command) {
        render(report(command) as JSON)
    }

    def patch(RequestPathCommand command) {
        render(report(command) as JSON)
    }

    def delete() {
        render(report() as JSON)
    }

    def forwarding() {
        forward(action: 'forwarded')
    }

    def forwarded() {
        render(report() as JSON)
    }

    private Map report(RequestPathCommand command = null) {
        def file = request.contentType?.startsWith('multipart/') ? request.getFile('file') : null
        [
                action        : actionName,
                // what the application sees: the method the request arrived as
                requestMethod : request.method,
                // what routed: the mapping picked this action for the overridden method
                params        : [id: params.id, description: params.description],
                commandBound  : command?.description,
                filename      : file?.empty ? null : file?.originalFilename,
                forwarded     : request.forwardURI != null
        ]
    }
}

class RequestPathCommand {
    String description
}
