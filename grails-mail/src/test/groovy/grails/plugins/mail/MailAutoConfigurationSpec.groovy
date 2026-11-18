/*
 * Copyright 2025 the original author or authors.
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

import java.util.function.Supplier

import javax.naming.Context
import javax.naming.spi.InitialContextFactory

import jakarta.mail.Session

import grails.core.GrailsApplication
import grails.plugins.GrailsPluginManager
import grails.web.pages.GroovyPagesUriService
import org.grails.gsp.GroovyPagesTemplateEngine
import org.grails.web.pages.DefaultGroovyPagesUriService
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.JavaMailSenderImpl
import spock.lang.Specification

class MailAutoConfigurationSpec extends Specification {

    GrailsApplication grailsApplication = Stub()
    GrailsPluginManager pluginManager = Stub()

    // mailMessageContentRenderer needs the GSP collaborators a real application gets from the
    // GSP plugin; every context here supplies them so the eager mail bean chain can start.
    private ApplicationContextRunner contextRunner() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MailAutoConfiguration))
                .withBean(GroovyPagesTemplateEngine, () -> new GroovyPagesTemplateEngine())
                .withBean(GroovyPagesUriService, () -> new DefaultGroovyPagesUriService())
                .withBean(GrailsApplication, () -> grailsApplication)
                .withBean(GrailsPluginManager, () -> pluginManager)
    }

    void 'the mail sender registers with defaults when no mailSession bean or configuration is present'() {
        expect: 'the optional @Autowired(required = false) @Qualifier session parameter carried through: a required parameter would fail refresh, since no mailSession bean exists'
        contextRunner().run { context ->
            JavaMailSenderImpl mailSender = context.getBean('mailSender', JavaMailSenderImpl)
            assert mailSender.host == (System.getenv('SMTP_HOST') ?: 'localhost')
            assert mailSender.defaultEncoding == 'utf-8'
        }
    }

    void 'the mail sender is configured from grails.mail properties'() {
        expect:
        contextRunner().withPropertyValues(
                'grails.mail.host=smtp.example.org',
                'grails.mail.port=2525',
                'grails.mail.username=mailer',
                'grails.mail.encoding=ISO-8859-1')
                .run { context ->
                    JavaMailSenderImpl mailSender = context.getBean('mailSender', JavaMailSenderImpl)
                    assert mailSender.host == 'smtp.example.org'
                    assert mailSender.port == 2525
                    assert mailSender.username == 'mailer'
                    assert mailSender.defaultEncoding == 'ISO-8859-1'
                }
    }

    void 'the JNDI mailSession bean is only declared when grails.mail.jndiName is set'() {
        expect:
        contextRunner().run { context ->
            assert !context.containsBean('mailSession')
        }
    }

    void 'the JNDI mail session registers when grails.mail.jndiName is set and is injected into the mail sender'() {
        given: 'a Session bound in JNDI under the configured name'
        Session boundSession = Session.getInstance(new Properties())
        TestMailSessionContextFactory.boundSession = boundSession

        expect:
        contextRunner()
                .withSystemProperties("java.naming.factory.initial=${TestMailSessionContextFactory.name}")
                .withPropertyValues('grails.mail.jndiName=mail/testSession')
                .run { context ->
                    assert context.containsBean('mailSession')
                    assert context.getBean('mailSession').is(boundSession)
                    JavaMailSenderImpl mailSender = context.getBean('mailSender', JavaMailSenderImpl)
                    assert mailSender.session.is(boundSession)
                    assert mailSender.host == null
                    assert mailSender.defaultEncoding == null
                }

        cleanup:
        TestMailSessionContextFactory.boundSession = null
    }

    void 'a user-defined mailSession bean is injected into the mail sender through the qualified optional parameter'() {
        given:
        Session userSession = Session.getInstance(new Properties())
        Supplier<Session> userSessionSupplier = () -> userSession

        expect:
        contextRunner().withBean('mailSession', Session, userSessionSupplier)
                .run { context ->
                    assert context.getBean('mailSender', JavaMailSenderImpl).session.is(userSession)
                }
    }

    void 'a user-defined mail sender makes the auto-configured one back off'() {
        given:
        JavaMailSender userMailSender = new JavaMailSenderImpl()
        Supplier<JavaMailSender> userMailSenderSupplier = () -> userMailSender

        expect:
        contextRunner().withBean('customMailSender', JavaMailSender, userMailSenderSupplier)
                .run { context ->
                    assert !context.containsBean('mailSender')
                    assert context.getBean(JavaMailSender).is(userMailSender)
                }
    }

    void 'the full mail service chain registers'() {
        expect:
        contextRunner().run { context ->
            assert context.containsBean('mailMessageContentRenderer')
            assert context.containsBean('mailMessageBuilderFactory')
            assert context.containsBean('mailService')
            assert context.getBean(MailService) != null
        }
    }

    static class TestMailSessionContextFactory implements InitialContextFactory {

        static Session boundSession

        @Override
        Context getInitialContext(Hashtable<?, ?> environment) {
            [lookup: { String name -> boundSession }, close: { }] as Context
        }
    }
}
