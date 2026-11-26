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
 * Implemented by command adapters whose underlying target should be autowired and inspected. The
 * compiler does not register a class that implements it, and a runner runs the adapter with the
 * application, or without it, as its target says.
 *
 * <p>Internal: what an {@link ApplicationCommandProvider}, which is deprecated, registers.</p>
 *
 * @since 8.0
 * @deprecated internal, and kept for one major version only: register a tool by its class
 */
@Deprecated
interface ApplicationCommandTargetAware {

    Object getTarget()
}
