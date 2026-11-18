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

/**
 * Restricts nothing, and forwards into an action of another controller that does.
 *
 * <p>Every action records that it has begun handling the request, restricted or not, so the action forwarded
 * into does not check the method this request arrived as against its own {@code allowedMethods} - a check
 * that would be about the wrong request. This controller is the case that rule exists for: the action which
 * reads the record is in the controller entered second, so the one entered first cannot know to write it
 * from anything it can see about itself.</p>
 */
class RequestPathForwarderController {

    static responseFormats = ['json']

    def forwardToRestricted() {
        forward(controller: 'requestPath', action: 'delete')
    }
}
