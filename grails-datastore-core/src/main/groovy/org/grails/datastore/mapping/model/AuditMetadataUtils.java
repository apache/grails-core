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
package org.grails.datastore.mapping.model;

import java.lang.reflect.Field;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ReflectionUtils;

import org.grails.datastore.mapping.config.AuditMetadataType;
import org.grails.datastore.mapping.config.Property;

/**
 * Utility class for detecting and caching audit metadata annotations on domain properties.
 * This avoids repeated reflection calls by storing the annotation type in the Property metadata.
 *
 * <p>Supports the following annotations (both GORM and Spring Data variants):</p>
 * <ul>
 *   <li>{@code @CreatedDate} / {@code @grails.gorm.annotation.CreatedDate} - automatically set on insert</li>
 *   <li>{@code @LastModifiedDate} / {@code @grails.gorm.annotation.LastModifiedDate} - automatically set on insert and update</li>
 *   <li>{@code @CreatedBy} / {@code @grails.gorm.annotation.CreatedBy} - automatically populated with current auditor on insert</li>
 *   <li>{@code @LastModifiedBy} / {@code @grails.gorm.annotation.LastModifiedBy} - automatically populated with current auditor on insert and update</li>
 *   <li>{@code @AutoTimestamp} - GORM-specific annotation for backwards compatibility</li>
 * </ul>
 *
 * <p>Caching behavior is controlled by the {@code grails.gorm.events.autoTimestampCacheAnnotations}
 * configuration property. When disabled (typically in development mode), annotation changes during
 * class reloading are immediately recognized. When enabled (production mode), annotations are cached
 * for optimal performance.</p>
 *
 * @author Scott Murphy Heiberg
 * @since 7.1
 */
public class AuditMetadataUtils {

    private static final Logger LOG = LoggerFactory.getLogger(AuditMetadataUtils.class);

    private static final String CREATED_DATE_ANNOTATION = "grails.gorm.annotation.CreatedDate";
    private static final String LAST_MODIFIED_DATE_ANNOTATION = "grails.gorm.annotation.LastModifiedDate";
    private static final String AUTO_TIMESTAMP_ANNOTATION = "grails.gorm.annotation.AutoTimestamp";
    private static final String CREATED_BY_ANNOTATION = "grails.gorm.annotation.CreatedBy";
    private static final String LAST_MODIFIED_BY_ANNOTATION = "grails.gorm.annotation.LastModifiedBy";

    private static final String CREATED_DATE_SPRING_ANNOTATION = "org.springframework.data.annotation.CreatedDate";
    private static final String LAST_MODIFIED_DATE_SPRING_ANNOTATION = "org.springframework.data.annotation.LastModifiedDate";
    private static final String CREATED_BY_SPRING_ANNOTATION = "org.springframework.data.annotation.CreatedBy";
    private static final String LAST_MODIFIED_BY_SPRING_ANNOTATION = "org.springframework.data.annotation.LastModifiedBy";

    /**
     * Gets the audit metadata type for a persistent property, using cached metadata when caching is enabled.
     *
     * <p>When caching is disabled (typically in development mode), this method will always perform
     * reflection to detect the current annotation state, ensuring that annotation changes during
     * class reloading are immediately recognized. When caching is enabled (production mode), the
     * result is cached to avoid repeated reflection calls.</p>
     *
     * @param persistentProperty The persistent property to check
     * @param cacheAnnotations Whether to cache the annotation metadata
     * @return The audit metadata type (CREATED, UPDATED, CREATED_BY, UPDATED_BY, or NONE)
     */
    public static AuditMetadataType getAuditMetadataType(PersistentProperty<?> persistentProperty, boolean cacheAnnotations) {
        Property mappedForm = persistentProperty.getMapping().getMappedForm();

        // If caching is disabled, always detect fresh to support class reloading
        if (!cacheAnnotations) {
            return detectAuditMetadataType(persistentProperty);
        }

        // Return cached value if available
        if (mappedForm.getAuditMetadataType() != null) {
            return mappedForm.getAuditMetadataType();
        }

        // Detect and cache the annotation type
        AuditMetadataType type = detectAuditMetadataType(persistentProperty);
        mappedForm.setAuditMetadataType(type);
        return type;
    }

    /**
     * Detects the audit metadata annotation type on a property using reflection.
     *
     * <p>When caching is enabled (production mode), this method is called once per property
     * and the result is cached. When caching is disabled (development mode), this method
     * is called on every access to ensure annotation changes are detected.</p>
     *
     * @param persistentProperty The persistent property to check
     * @return The audit metadata type (CREATED, UPDATED, CREATED_BY, UPDATED_BY, or NONE)
     */
    private static AuditMetadataType detectAuditMetadataType(PersistentProperty<?> persistentProperty) {
        try {
            Field field = ReflectionUtils.findField(
                persistentProperty.getOwner().getJavaClass(),
                persistentProperty.getName()
            );

            if (field != null) {
                for (java.lang.annotation.Annotation annotation : field.getDeclaredAnnotations()) {
                    String annotationName = annotation.annotationType().getName();

                    switch (annotationName) {
                        case CREATED_DATE_ANNOTATION:
                        case CREATED_DATE_SPRING_ANNOTATION:
                            return AuditMetadataType.CREATED;

                        case LAST_MODIFIED_DATE_ANNOTATION:
                        case LAST_MODIFIED_DATE_SPRING_ANNOTATION:
                            return AuditMetadataType.UPDATED;

                        case CREATED_BY_ANNOTATION:
                        case CREATED_BY_SPRING_ANNOTATION:
                            return AuditMetadataType.CREATED_BY;

                        case LAST_MODIFIED_BY_ANNOTATION:
                        case LAST_MODIFIED_BY_SPRING_ANNOTATION:
                            return AuditMetadataType.UPDATED_BY;

                        case AUTO_TIMESTAMP_ANNOTATION:
                            // For @AutoTimestamp, check the EventType value
                            try {
                                Object eventTypeValue = annotation.annotationType()
                                    .getMethod("value")
                                    .invoke(annotation);

                                if (eventTypeValue != null) {
                                    String eventTypeName = eventTypeValue.toString();
                                    if (eventTypeName.equals("UPDATED")) {
                                        return AuditMetadataType.UPDATED;
                                    } else {
                                        return AuditMetadataType.CREATED;
                                    }
                                }
                            } catch (Exception e) {
                                // If we can't read the value, default to CREATED
                                if (LOG.isDebugEnabled()) {
                                    LOG.debug("Failed to read @AutoTimestamp EventType value for property [{}], defaulting to CREATED",
                                            persistentProperty.getName(), e);
                                }
                                return AuditMetadataType.CREATED;
                            }
                            break;
                    }
                }
            }
        } catch (Exception e) {
            // If reflection fails, return NONE
            if (LOG.isDebugEnabled()) {
                LOG.debug("Failed to detect audit metadata annotations for property [{}] on entity [{}]",
                        persistentProperty.getName(),
                        persistentProperty.getOwner().getName(), e);
            }
        }

        return AuditMetadataType.NONE;
    }

    /**
     * Checks if a property has any audit metadata annotation.
     *
     * @param persistentProperty The persistent property to check
     * @param cacheAnnotations Whether to cache the annotation metadata
     * @return true if the property has any supported annotation ({@code @CreatedDate}, {@code @LastModifiedDate},
     *         {@code @CreatedBy}, {@code @LastModifiedBy}, or {@code @AutoTimestamp}) from either GORM or Spring Data
     */
    public static boolean hasAuditMetadataAnnotation(PersistentProperty<?> persistentProperty, boolean cacheAnnotations) {
        return persistentProperty != null && getAuditMetadataType(persistentProperty, cacheAnnotations) != AuditMetadataType.NONE;
    }
}
