/*
 * Copyright 2008-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.plugins.mail

import grails.core.GrailsApplication
import grails.plugins.GrailsPluginManager
import grails.plugins.Plugin
import grails.plugins.metadata.GrailsPluginDescriptor
import grails.web.pages.GroovyPagesUriService
import groovy.transform.CompileStatic
import jakarta.mail.Session
import org.grails.gsp.GroovyPagesTemplateEngine
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.jndi.JndiObjectFactoryBean
import org.springframework.mail.MailSender
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.JavaMailSenderImpl

@CompileStatic
@AutoConfiguration
@EnableConfigurationProperties(MailConfigurationProperties)
@GrailsPluginDescriptor(providedArtefacts = [PlainTextMailTagLib])
class MailGrailsPlugin extends Plugin {

    def beans = {
        bean('mailSession', JndiObjectFactoryBean).conditionalOnMissingBean().annotate(ConditionalOnProperty, prefix: 'grails.mail', name: 'jndiName') { MailConfigurationProperties mailProperties ->
            new JndiObjectFactoryBean().tap {
                jndiName = mailProperties.jndiName
            }
        }

        bean('mailSender', JavaMailSender).conditionalOnMissingBean() { @Autowired(required = false) @Qualifier('mailSession') Session mailSession,
                MailConfigurationProperties mailProperties ->
            new JavaMailSenderImpl().tap {
                if (mailProperties.host || !mailProperties.jndiName) {
                    host = mailProperties.host ?: System.getenv('SMTP_HOST') ?: 'localhost'
                }
                if (mailProperties.encoding || !mailProperties.jndiName) {
                    defaultEncoding = mailProperties.encoding ?: 'utf-8'
                }
                if (mailSession) {
                    session = mailSession
                }
                if (mailProperties.port) {
                    port = mailProperties.port
                }
                if (mailProperties.username) {
                    username = mailProperties.username
                }
                if (mailProperties.password) {
                    password = mailProperties.password
                }
                if (mailProperties.protocol) {
                    protocol = mailProperties.protocol
                }
                if (mailProperties.props) {
                    javaMailProperties = mailProperties.props
                }
            }
        }

        bean(MailMessageBuilderFactory).conditionalOnMissingBean() {
                MailSender mailSender,
                MailMessageContentRenderer mailMessageContentRenderer ->
        }

        bean(MailMessageContentRenderer).conditionalOnMissingBean() {
                GroovyPagesTemplateEngine groovyPagesTemplateEngine,
                GroovyPagesUriService groovyPagesUriService,
                GrailsApplication grailsApplication,
                GrailsPluginManager pluginManager ->
        }

        bean(MailService).conditionalOnMissingBean() {
                MailConfigurationProperties mailConfigurationProperties,
                MailMessageBuilderFactory mailMessageBuilderFactory ->
        }
    }
}
