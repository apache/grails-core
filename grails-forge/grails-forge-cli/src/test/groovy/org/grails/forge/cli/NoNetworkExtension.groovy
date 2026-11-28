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
package org.grails.forge.cli

import java.util.function.Consumer
import org.apache.grails.forge.cli.pack.PackArtifacts
import org.spockframework.runtime.extension.IGlobalExtension

/**
 * Keeps every specification of this module from the network: a request for the jar of a plugin or for
 * a feature catalog to a host that is not this computer fails the specification that made it, with the
 * address, and is not made. Spock starts this before the first specification of a test JVM.
 */
class NoNetworkExtension implements IGlobalExtension {

    static final Set<String> THIS_COMPUTER = ['localhost', '127.0.0.1', '[::1]'] as Set<String>

    @Override
    void start() {
        PackArtifacts.beforeEachRequest({ URI address ->
            if (!THIS_COMPUTER.contains(address.host?.toLowerCase(Locale.ROOT))) {
                throw new PackArtifacts.RequestRefused("A request was made to ${address}, and the specifications of the Forge CLI ask nothing of another computer")
            }
        } as Consumer<URI>)
    }
}
