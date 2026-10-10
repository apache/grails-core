/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package liquibase.ext.hibernate.snapshot

import groovy.transform.CompileStatic
import liquibase.Scope
import org.hibernate.boot.model.naming.Identifier
import org.hibernate.boot.model.relational.QualifiedName
import org.hibernate.boot.model.relational.SqlStringGenerationContext
import org.hibernate.boot.model.relational.internal.SqlStringGenerationContextImpl
import org.hibernate.generator.Generator
import org.hibernate.id.NativeGenerator
import org.hibernate.mapping.GeneratorSettings
import org.hibernate.mapping.SimpleValue

/**
 * Shared helpers for the snapshot generators that inspect identifier generators.
 */
@CompileStatic
final class IdentifierGeneratorSupport {

    private IdentifierGeneratorSupport() {
    }

    /**
     * Uses the configured creator when available, otherwise checks explicit annotation or mapping intent.
     */
    static boolean hasGenerationIntent(SimpleValue simpleValue) {
        def creator = simpleValue.getCustomIdGeneratorCreator()
        if (creator != null) {
            return !creator.isAssigned()
        }
        def memberDetails = simpleValue.getMemberDetails()
        return memberDetails == null ||
                memberDetails.hasDirectAnnotationUsage(jakarta.persistence.GeneratedValue) ||
                memberDetails.hasDirectAnnotationUsage(org.hibernate.annotations.NativeGenerator)
    }

    static SequenceKey sequenceKey(QualifiedName name) {
        return new SequenceKey(canonicalName(name.getCatalogName()), canonicalName(name.getSchemaName()),
                canonicalName(name.getObjectName()))
    }

    private static String canonicalName(Identifier identifier) {
        return identifier == null ? null : identifier.getCanonicalName()
    }

    record SequenceKey(String catalog, String schema, String name) {
    }

    /**
     * Hibernate does not expose the generator a {@link NativeGenerator} delegates to, so it is read reflectively.
     *
     * @return the delegate, or {@code null} if it cannot be resolved
     */
    static Generator nativeDelegate(NativeGenerator nativeGen) {
        try {
            def field = NativeGenerator.getDeclaredField('dialectNativeGenerator')
            field.setAccessible(true)
            return (Generator) field.get(nativeGen)
        } catch (ReflectiveOperationException | RuntimeException e) {
            Scope.getCurrentScope().getLog(IdentifierGeneratorSupport)
                    .fine('Could not access NativeGenerator delegate', e)
            return null
        }
    }

    static GeneratorSettings createGeneratorSettings(SimpleValue simpleValue) {
        def buildingContext = simpleValue.getBuildingContext()
        return new GeneratorSettings() {
            @Override
            String getDefaultCatalog() {
                return null
            }

            @Override
            String getDefaultSchema() {
                return null
            }

            @Override
            SqlStringGenerationContext getSqlStringGenerationContext() {
                def db = buildingContext.getMetadataCollector().getDatabase()
                return SqlStringGenerationContextImpl.fromExplicit(
                        db.getJdbcEnvironment(), db, getDefaultCatalog(), getDefaultSchema())
            }
        }
    }

}
