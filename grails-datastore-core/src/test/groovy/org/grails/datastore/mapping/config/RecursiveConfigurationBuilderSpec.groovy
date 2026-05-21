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

class RecursiveConfigurationBuilderSpec extends Specification {

    void "recursive builders don't cause stackoverflow"() {
        given:
        PropertyResolver config = DatastoreUtils.createPropertyResolver([:])

        when:
        MongoConnectionSourceSettingsBuilder builder = new MongoConnectionSourceSettingsBuilder(config, "grails.mongodb");
        MongoConnectionSourceSettings settings = builder.build()

        then:
        noExceptionThrown()
        !settings.options.autoEncryptionSettings
    }

    void "recursive builder get configured correctly"() {
        given:
        PropertyResolver config = DatastoreUtils.createPropertyResolver(["grails.mongodb.options.autoEncryptionSettings.bypassAutoEncryption": true])

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

    static class MongoClientSettings {

        private AutoEncryptionSettings autoEncryptionSettings

        MongoClientSettings(Builder builder) {
            autoEncryptionSettings = builder.autoEncryptionSettings
        }

        static Builder builder() {
              new Builder()
        }

        static class Builder {

            private AutoEncryptionSettings autoEncryptionSettings;

            private Builder() {

            }

            Builder autoEncryptionSettings(AutoEncryptionSettings autoEncryptionSettings) {
                this.autoEncryptionSettings = autoEncryptionSettings
                this
            }

            MongoClientSettings build() {
                new MongoClientSettings(this)
            }
        }
    }

    static class AutoEncryptionSettings {
        private MongoClientSettings keyVaultMongoClientSettings;
        private boolean bypassAutoEncryption

        private AutoEncryptionSettings(Builder builder) {
            this.keyVaultMongoClientSettings = builder.keyVaultMongoClientSettings
            this.bypassAutoEncryption = builder.bypassAutoEncryption
        }

        static Builder builder() {
            new Builder()
        }

        static class Builder {
            private MongoClientSettings keyVaultMongoClientSettings
            private boolean bypassAutoEncryption

            private Builder() {
            }

            Builder bypassAutoEncryption(boolean bypassAutoEncryption) {
                this.bypassAutoEncryption = bypassAutoEncryption
                this
            }

            Builder keyVaultMongoClientSettings(MongoClientSettings keyVaultMongoClientSettings) {
                this.keyVaultMongoClientSettings = keyVaultMongoClientSettings
                this
            }

            AutoEncryptionSettings build() {
                new AutoEncryptionSettings(this)
            }
        }
    }
}
