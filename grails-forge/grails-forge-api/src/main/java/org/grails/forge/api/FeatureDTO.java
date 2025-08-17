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

import io.micronaut.context.MessageSource;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.naming.Described;
import io.micronaut.core.naming.Named;
import org.grails.forge.feature.Feature;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents an application feature.
 *
 * @author graemerocher
 * @since 6.0.0
 */
@Introspected
@Schema(name = "Feature")
public class FeatureDTO extends Linkable implements Named, Described {

    static final String MESSAGE_PREFIX = GrailsForgeConfiguration.PREFIX + ".features.";

    private final String name;
    private final String title;
    private final String description;
    private final String category;
    private final boolean preview;
    private final boolean community;

    /**
     * Default constructor.
     * @param feature The feature
     * @param messageSource The message source
     * @param messageContext The messageContext
     */
    public FeatureDTO(Feature feature, MessageSource messageSource, MessageSource.MessageContext messageContext) {
        this.name = feature.getName();
        this.title = messageSource.getMessage(MESSAGE_PREFIX + this.name + ".title", messageContext, feature.getTitle());
        this.description = messageSource.getMessage(MESSAGE_PREFIX + this.name + ".description", messageContext, feature.getDescription());
        this.category = feature.getCategory();
        this.preview = feature.isPreview();
        this.community = feature.isCommunity();
    }

    /**
     * Default constructor.
     * @param name The name
     * @param title The title
     * @param description The description
     * @param category The category
     */
    @Creator
    public FeatureDTO(String name, String title, String description, String category) {
        this.name = name;
        this.title = title;
        this.description = description;
        this.category = category;
        this.preview = false;
        this.community = false;
    }

    /**
     * @return The name of the feature
     */
    @Schema(description = "The name of the feature")
    public String getName() {
        return name;
    }

    /**
     * @return The title of the feature
     */
    @Schema(description = "The title of the feature")
    public String getTitle() {
        return title;
    }

    /**
     * @return The description of the feature
     */
    @Schema(description = "A description of the feature")
    public String getDescription() {
        return description;
    }

    /**
     * @return The category to which this feature belongs to
     */
    @Schema(description = "The category to which this feature belongs to")
    public String getCategory() {
        return category;
    }

    /**
     * @return Is the feature a preview status feature
     */
    @Schema(description = "Indicates whether the feature is a preview feature and subject to change")
    public boolean isPreview() {
        return preview;
    }

    /**
     * @return Is the feature a community contributed feature.
     */
    @Schema(description = "Indicates whether the feature is a community contributed feature")
    public boolean isCommunity() {
        return community;
    }
}
