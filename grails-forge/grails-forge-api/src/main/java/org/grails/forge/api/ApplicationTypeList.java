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
package org.grails.forge.api;

import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Models a list of application types.
 *
 * @author graemerocher
 * @since 6.0.0
 */
@Schema(description = "A list of application types")
@Introspected
public class ApplicationTypeList extends Linkable {
    private List<ApplicationTypeDTO> types;

    /**
     * Constructor.
     */
    public ApplicationTypeList() {
    }

    /**
     *
     * @param types The application types
     */
    public ApplicationTypeList(List<ApplicationTypeDTO> types) {
        this.types = types;
    }

    /**
     * @return The application types
     */
    @Schema(description = "The application types")
    public List<ApplicationTypeDTO> getTypes() {
        return types;
    }

    /**
     *
     * @param types The application types
     */
    public void setTypes(List<ApplicationTypeDTO> types) {
        this.types = types;
    }
}
