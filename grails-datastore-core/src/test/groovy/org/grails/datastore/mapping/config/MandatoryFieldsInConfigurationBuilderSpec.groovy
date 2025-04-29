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
package org.grails.datastore.mapping.config

import groovy.transform.AutoClone
import groovy.transform.builder.Builder
import groovy.transform.builder.SimpleStrategy
import org.grails.datastore.mapping.core.DatastoreUtils
import org.springframework.core.env.PropertyResolver
import spock.lang.Specification

class MandatoryFieldsInConfigurationBuilderSpec extends Specification {

    void "mandatory fields in optional child builders don't stop configuration"() {
        given:
        PropertyResolver config = DatastoreUtils.createPropertyResolver([:])

        when:
        MongoConnectionSourceSettingsBuilder builder = new MongoConnectionSourceSettingsBuilder(config, "grails.mongodb")
        builder.build()

        then:
        noExceptionThrown()
    }

    void "if you supply mandatory fields via configuration the builder uses them"() {
        given:
        PropertyResolver config = DatastoreUtils.createPropertyResolver([
                "grails.mongodb.options.autoEncryptionSettings.bypassAutoEncryption": true,
                "grails.mongodb.options.autoEncryptionSettings.keyVaultNamespace": false
        ])

        when:
        MongoConnectionSourceSettingsBuilder builder = new MongoConnectionSourceSettingsBuilder(config, "grails.mongodb")
        MongoConnectionSourceSettings settings = builder.build()

        then:
        noExceptionThrown()
        settings.options.autoEncryptionSettings.bypassAutoEncryption
    }

    class MongoConnectionSourceSettingsBuilder extends ConfigurationBuilder<MongoConnectionSourceSettings, MongoConnectionSourceSettings>{

        MongoConnectionSourceSettingsBuilder(PropertyResolver propertyResolver, String configurationPrefix, Object fallBackConfiguration, String builderMethodPrefix) {
            super(propertyResolver, configurationPrefix, fallBackConfiguration, builderMethodPrefix)
        }

        MongoConnectionSourceSettingsBuilder(PropertyResolver propertyResolver, String configurationPrefix, Object fallBackConfiguration) {
            super(propertyResolver, configurationPrefix, fallBackConfiguration)
        }

        MongoConnectionSourceSettingsBuilder(PropertyResolver propertyResolver, String configurationPrefix) {
            super(propertyResolver, configurationPrefix)
        }

        MongoConnectionSourceSettingsBuilder(PropertyResolver propertyResolver, String configurationPrefix, String builderMethodPrefix) {
            super(propertyResolver, configurationPrefix, builderMethodPrefix)
        }

        @Override
        protected MongoConnectionSourceSettings createBuilder() {
            new MongoConnectionSourceSettings()
        }

        @Override
        protected MongoConnectionSourceSettings toConfiguration(MongoConnectionSourceSettings builder) {
            builder
        }
    }

    @AutoClone
    @Builder(builderStrategy = SimpleStrategy, prefix = '')
    static class MongoConnectionSourceSettings {
        MongoClientOptions.Builder options = MongoClientOptions.builder()

    }

    static class MongoClientOptions {

        private AutoEncryptionSettings autoEncryptionSettings

        private MongoClientOptions(Builder builder) {
            autoEncryptionSettings = builder.autoEncryptionSettings;
        }

        static Builder builder() {
            new Builder()
        }

        static class Builder {

            private AutoEncryptionSettings autoEncryptionSettings

            Builder autoEncryptionSettings(AutoEncryptionSettings autoEncryptionSettings) {
                this.autoEncryptionSettings = autoEncryptionSettings
                this
            }

            MongoClientOptions build() {
                new MongoClientOptions(this)
            }
        }
    }


    static class AutoEncryptionSettings {
        private boolean bypassAutoEncryption
        private String keyVaultNamespace

        private AutoEncryptionSettings(Builder builder) {
            this.bypassAutoEncryption = builder.bypassAutoEncryption
            this.keyVaultNamespace = notNull("keyVaultNamespace", builder.keyVaultNamespace)
        }

        static Builder builder() {
            new Builder()
        }

        static String notNull(String name, Object value) {
            if (value == null) {
                throw new IllegalArgumentException(name + " can not be null");
            }
            value
        }

        static class Builder {
            private boolean bypassAutoEncryption
            private String keyVaultNamespace

            private Builder() {
            }

            Builder keyVaultNamespace(String keyVaultNamespace) {
                this.keyVaultNamespace = notNull("keyVaultNamespace", keyVaultNamespace)
                return this
            }

            Builder bypassAutoEncryption(boolean bypassAutoEncryption) {
                this.bypassAutoEncryption = bypassAutoEncryption
                this
            }

            AutoEncryptionSettings build() {
                new AutoEncryptionSettings(this)
            }
        }
    }
}
