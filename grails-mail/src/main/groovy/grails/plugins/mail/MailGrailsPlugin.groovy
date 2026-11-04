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
class MailGrailsPlugin extends Plugin {

    def grailsVersion = '7.0.0 > *'
    def author = 'Apache Grails Team'
    def authorEmail = ''
    def title = 'Provides Mail support to a running Grails application'
    def description = '''\
        This plugin provides a MailService class as well as configuring the necessary beans within
        the Spring ApplicationContext.

        It also adds a "sendMail" method to all controller classes. A typical example usage is:

        sendMail {
            to 'fred@g2one.com','ginger@g2one.com'
            from 'john@g2one.com'
            cc 'marge@g2one.com', 'ed@g2one.com'
            bcc 'joe@g2one.com'
            subject 'Hello John'
            text 'this is some text'
        }
    '''.stripIndent(8)
    def documentation = 'https://grails-plugins.github.io/grails-mail'
    def license = 'Apache 2.0 License'
    def organization = [name: 'Grails Plugins', url: 'https://github.com/grails-plugins']
    def issueManagement = [
        system: 'GitHub',
        url: 'https://github.com/grails-plugins/grails-mail/issues'
    ]
    def scm = [
        url: 'https://github.com/grails-plugins/grails-mail'
    ]
    def providedArtefacts = [PlainTextMailTagLib]

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
