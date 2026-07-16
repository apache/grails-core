/*
 * Copyright 2022-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.plugins.mail

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(PREFIX)
class MailConfigurationProperties {

    public static final String PREFIX = 'grails.mail'

    boolean disabled
    String overrideAddress
    Defaults defaults = new Defaults()

    Integer poolSize
    String encoding
    String jndiName
    String protocol
    String host
    Integer port
    String username
    String password
    Properties props

    /**
     * We can't use 'default' as a groovy property name as it is a reserved keyword.
     * But it is possible to define a getter for it to make it compatible.
     */
    Defaults getDefault() {
        return defaults
    }

    static class Defaults {
        String from
        String to
    }
}
