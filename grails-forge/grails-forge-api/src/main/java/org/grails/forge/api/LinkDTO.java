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

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents a link.
 *
 * @author graemerocher
 * @since 6.0.0
 */
@Introspected
@Schema(name = "Link")
public class LinkDTO {

    private final String href;
    private final boolean templated;

    public LinkDTO(String href) {
        this(href, false);
    }

    @Creator
    public LinkDTO(String href, boolean templated) {
        this.href = href;
        this.templated = templated;
    }

    /**
     * @return The link address
     */
    @Schema(description = "The link address")
    public String getHref() {
        return href;
    }

    /**
     * @return Whether the link is templated
     */
    @Schema(description = "Whether the link is templated")
    public boolean isTemplated() {
        return templated;
    }
}
