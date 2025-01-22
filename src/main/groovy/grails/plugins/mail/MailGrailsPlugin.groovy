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

import grails.plugins.Plugin

class MailGrailsPlugin extends Plugin {
    
    def grailsVersion = '7.0.0 > *'
    def author = 'The Grails team'
    def authorEmail = 'info@grails.org'
    def title = 'Provides Mail support to a running Grails application'
    def description = '''\
This plugin provides a MailService class as well as configuring the necessary beans within
the Spring ApplicationContext.

It also adds a "sendMail" method to all controller classes. A typical example usage is:

sendMail {
    to "fred@g2one.com","ginger@g2one.com"
    from "john@g2one.com"
    cc "marge@g2one.com", "ed@g2one.com"
    bcc "joe@g2one.com"
    subject "Hello John"
    text "this is some text"
}
'''
    def documentation = 'https://gpc.github.io/grails-mail/'

    def license = 'Apache 2.0 License'
    def organization = [name: 'Grails Plugin Collective', url: 'https://github.com/gpc']
    def developers = [
        [name: 'Craig Andrews', email: 'candrews@integralblue.com'],
        [name: 'Luke Daley', email: 'ld@ldaley.com'],
        [name: 'Peter Ledbrook', email: 'p.ledbrook@cacoethes.co.uk'],
        [name: 'Jeff Brown', email: 'brownj@ociweb.com'],
        [name: 'Graeme Rocher', email: 'rocherg@ociweb.com'],
        [name: 'Marc Palmer', email: 'marc@grailsrocks.com'],
        [name: 'Søren Berg Glasius', email: 'soeren@glasius.dk'],
        [name: 'Mattias Reichel', email: 'mattias.reichel@gmail.com']
    ]

    def issueManagement = [system: 'GitHub', url: 'https://github.com/gpc/grails-mail/issues']
    def scm = [url: 'https://github.com/gpc/grails-mail']

    def observe = ['controllers', 'services']

    def pluginExcludes = [
        'grails-app/i18n/*.properties',
        'grails-app/views/_testemails/*.gsp'
    ]

    @Override
    Closure doWithSpring() {
        return {
            mailConfiguration(MailConfiguration)
        }
    }
}
