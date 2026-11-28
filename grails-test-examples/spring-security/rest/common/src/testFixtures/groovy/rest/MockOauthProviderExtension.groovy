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

package rest

import groovy.transform.CompileStatic

import org.spockframework.runtime.extension.IGlobalExtension

/**
 * Stops the {@link MockOauthProvider} once every spec of the test JVM has run. The server cannot be stopped by a
 * spec: the specs of an application share one application context, which holds the URLs of the server.
 */
@CompileStatic
class MockOauthProviderExtension implements IGlobalExtension {

    @Override
    void stop() {
        MockOauthProvider.stop()
    }
}
