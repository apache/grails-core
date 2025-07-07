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
package org.grails.forge.feature.config;

import io.micronaut.core.annotation.NonNull;

public class BootstrapConfiguration extends Configuration {

    public BootstrapConfiguration(@NonNull String sourceSet, @NonNull String environment) {
        super(sourceSet, ("bootstrap-" + environment), ("bootstrap-config-" + environment), environment);
    }

    public BootstrapConfiguration(@NonNull String environment) {
        this("main", environment);
    }

    public BootstrapConfiguration() {
        super("main", "bootstrap", "bootstrap-config", null);
    }

    public static BootstrapConfiguration testConfig() {
        return new BootstrapConfiguration("test", "test");
    }
}
