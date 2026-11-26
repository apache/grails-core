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
package org.apache.grails.core.cli

/**
 * Contributes application commands discovered outside the primary command contract.
 *
 * <p>Internal: this is how the compatibility layer of Grails 8 for the commands of Grails 7 put
 * its commands into the registry, and nothing in Grails implements it any more. The registry still
 * calls a provider that is registered under its name in {@code META-INF/grails-cli.factories}. A
 * tool is registered by its class in {@code META-INF/grails-cli.factories}, which the compiler
 * writes; that is the only supported way.</p>
 *
 * @since 8.0
 * @deprecated internal, and kept for one major version only: register a tool by its class
 */
@Deprecated
interface ApplicationCommandProvider {

    void contributeCommands(
        ClassLoader registryClassLoader,
        ClassLoader contextClassLoader,
        ApplicationCommandRegistrar registrar)
}
