/*
 * Copyright 2004-2025 the original author or authors.
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

import grails.testing.mixin.integration.Integration
import groovy.xml.XmlSlurper
import jakarta.inject.Inject
import jakarta.mail.Message
import jakarta.mail.Multipart
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import org.grails.io.support.ClassPathResource
import org.springframework.core.io.FileSystemResource
import org.springframework.mail.MailMessage
import org.springframework.mail.MailSender
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMailMessage
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.web.context.request.RequestContextHolder
import spock.lang.Ignore
import spock.lang.Specification
import testapp1.GreenMailService

@Integration
class MailServiceSpec extends Specification {

    @Inject
    JavaMailSender mimeMailSender
    GreenMailService greenMailService

    MailService mimeCapableMailService
    MailService nonMimeCapableMailService
    MailMessageContentRenderer mailMessageContentRenderer

    def setup() {
        def mimeMessageBuilderFactory = new MailMessageBuilderFactory(
                mimeMailSender,
                mailMessageContentRenderer
        )
        mimeCapableMailService = new MailService(
                new MailConfigurationProperties(),
                mimeMessageBuilderFactory
        )
        mimeCapableMailService.afterPropertiesSet()

        def simpleMailSender = new SimpleMailSender()
        def simpleMessageBuilderFactory = new MailMessageBuilderFactory(
                simpleMailSender,
                mailMessageContentRenderer
        )
        nonMimeCapableMailService = new MailService(
                new MailConfigurationProperties(),
                simpleMessageBuilderFactory
        )
        nonMimeCapableMailService.afterPropertiesSet()
    }

    def cleanup() {
        mimeCapableMailService.destroy()
        nonMimeCapableMailService.destroy()
        greenMailService.reset()
    }

    void 'should send a simple mail message successfully'() {
        given: 'a mail recipient, subject, body, and sender'
        String recipient = 'fred@g2one.com'
        String subject = 'Hello John'
        String bodyText = 'this is some text'
        String sender = 'king@g2one.com'

        when: 'the mail service sends the message'
        def message = nonMimeCapableMailService.sendMail {
            to recipient
            title subject
            body bodyText
            from sender
        }

        then: 'the message is a SimpleMailMessage and has correct properties'
        message instanceof SimpleMailMessage
        def smm = (SimpleMailMessage) message
        smm.subject == subject
        smm.text == bodyText
        smm.to[0] == recipient
        smm.from == sender
    }

    void 'should send a simple mail message asynchronously'() {
        given: 'a mail recipient, subject, body, and sender'
        String recipient = 'fred@g2one.com'
        String subject = 'Hello John'
        String bodyText = 'this is some text'
        String sender = 'king@g2one.com'

        when: 'the mail service sends the message asynchronously'
        def message = nonMimeCapableMailService.sendMail {
            async true
            to recipient
            title subject
            body bodyText
            from sender
        }

        then: 'the message is a SimpleMailMessage and has the correct properties'
        message instanceof SimpleMailMessage
        def smm = (SimpleMailMessage) message
        smm.subject == subject
        smm.text == bodyText
        smm.to[0] == recipient
        smm.from == sender
    }

    void 'should send a mail message to multiple recipients'() {
        given: 'multiple mail recipients and a subject and body'
        String recipient1 = 'fred@g2one.com'
        String recipient2 = 'ginger@g2one.com'
        String subject = 'Hello John'
        String bodyText = 'this is some text'

        when: 'the mail service sends the message to multiple recipients'
        def message = (SimpleMailMessage) nonMimeCapableMailService.sendMail {
            to recipient1, recipient2
            title subject
            body bodyText
        }

        then: 'the message has the correct subject, body, and recipients'
        message.subject == subject
        message.text == bodyText
        message.to.size() == 2
        message.to[0] == recipient1
        message.to[1] == recipient2
    }

    void 'should send a mail message to multiple recipients using a list'() {
        given: 'a list of recipients, subject, and body for the mail message'
        List<String> recipients = ['fred@g2one.com', 'ginger@g2one.com']
        String subject = 'Hello John'
        String bodyText = 'this is some text'

        when: 'the mail service sends the message to the recipients list'
        def message = (SimpleMailMessage) nonMimeCapableMailService.sendMail {
            to recipients
            title subject
            body bodyText
        }

        then: 'the message should have the correct recipients'
        message.to.size() == 2
        message.to[0] == recipients[0]
        message.to[1] == recipients[1]
    }

    void 'should send a mail message with multiple CC recipients using a list'() {
        given: 'a recipient, CC recipients list, subject, and body for the mail message'
        String recipient = 'joe@g2one.com'
        List<String> ccRecipients = ['fred@g2one.com', 'ginger@g2one.com']
        String subject = 'Hello John'
        String bodyText = 'this is some text'

        when: 'the mail service sends the message with CC recipients'
        def message = (SimpleMailMessage) nonMimeCapableMailService.sendMail {
            to recipient
            cc ccRecipients
            title subject
            body bodyText
        }

        then: 'the message has the correct main recipient and CC recipients'
        message.to[0] == recipient
        message.cc.size() == 2
        message.cc[0] == ccRecipients[0]
        message.cc[1] == ccRecipients[1]
    }

    void 'should send a mail message with multiple BCC recipients using a list'() {
        given: 'a recipient, BCC recipients list, subject, and body for the mail message'
        String recipient = 'joe@g2one.com'
        List<String> bccRecipients = ['fred@g2one.com', 'ginger@g2one.com']
        String subject = 'Hello John'
        String bodyText = 'this is some text'

        when: 'the mail service sends the message with BCC recipients'
        def message = (SimpleMailMessage) nonMimeCapableMailService.sendMail {
            to recipient
            bcc bccRecipients
            title subject
            body bodyText
        }

        then: 'the message has the correct main recipient and BCC recipients'
        message.to[0] == recipient
        message.bcc.size() == 2
        message.bcc[0] == bccRecipients[0]
        message.bcc[1] == bccRecipients[1]
    }

    void 'should send a mail message to multiple recipients with CC and BCC'() {
        given: 'a list of recipients, CC recipients, BCC recipients, subject, body, and sender'
        List<String> toRecipients = ['fred@g2one.com', 'ginger@g2one.com']
        List<String> ccRecipients = ['marge@g2one.com', 'ed@g2one.com']
        List<String> bccRecipients = ['joe@g2one.com']
        String sender = 'john@g2one.com'
        String subject = 'Hello John'
        String bodyText = 'this is some text'

        when: 'the mail service sends the message with recipients, CC, BCC'
        def message = (SimpleMailMessage) nonMimeCapableMailService.sendMail {
            to toRecipients
            from sender
            cc ccRecipients
            bcc bccRecipients
            title subject
            body bodyText
        }

        then: 'the message is a SimpleMailMessage with correct recipients, CC, BCC, and content'
        message.subject == subject
        message.text == bodyText
        message.from == sender

        and: 'the to recipients are correct'
        message.to.length == toRecipients.size()
        message.to.toList() == toRecipients

        and: 'the cc recipients are correct'
        message.cc.length == ccRecipients.size()
        message.cc.toList() == ccRecipients

        and: 'the bcc recipients are correct'
        message.bcc.length == bccRecipients.size()
        message.bcc.toList() == bccRecipients
    }

    void 'should send mail with envelopeFrom correctly set'() {
        given: 'a mail message with a recipient, subject, body, from, and envelopeFrom'
        String recipient = 'fred@g2one.com'
        String sender = 'king@g2one.com'
        String envelopeSender = 'peter@g2one.com'
        String subject = 'Hello John'
        String bodyText = 'this is some text'

        when: 'the mail service sends the message with envelopeFrom'
        def message = (MimeMailMessage) mimeCapableMailService.sendMail {
            to recipient
            title subject
            body bodyText
            from sender
            envelopeFrom envelopeSender
        }

        and: 'the mime message and received message are retrieved'
        def msg = message.mimeMessage
        def greenMsg = greenMailService.greenMail.receivedMessages[0]

        then: 'the mime message should have the correct subject and sender'
        msg.subject == subject
        msg.from[0].toString() == sender

        and: 'the received message should have the correct envelopeFrom (Return-Path)'
        greenMsg.getHeader('Return-Path')[0] == "<$envelopeSender>"
    }

    void 'should throw GrailsMailException when using envelopeFrom with basic mail sender'() {
        given: 'a mail message with a recipient, subject, body, from, and envelopeFrom'
        String recipient = 'fred@g2one.com'
        String sender = 'king@g2one.com'
        String envelopeSender = 'peter@g2one.com'
        String subject = 'Hello John'
        String bodyText = 'this is some text'

        when: 'the mail service sends the message with envelopeFrom'
        nonMimeCapableMailService.sendMail {
            to recipient
            title subject
            body bodyText
            from sender
            envelopeFrom envelopeSender
        }

        then: 'a GrailsMailException is thrown'
        thrown GrailsMailException
    }

    void 'should send an HTML mail message successfully'() {
        given: 'a recipient and an HTML message content'
        String recipient = 'fred@g2one.com'
        String mailSubject = 'Hello John'
        String htmlContent = '<b>Hello</b> World'

        when: 'the mail service sends the HTML message'
        def message = (MimeMailMessage) mimeCapableMailService.sendMail {
            to recipient
            subject mailSubject
            html htmlContent
        }

        then: 'the message should have the correct subject, content type, and content'
        message.mimeMessage.subject == mailSubject
        message.mimeMessage.contentType.startsWith('text/html')
        message.mimeMessage.content == htmlContent
    }

    void 'should send a mail message using a view successfully'() {
        given: 'a recipient, subject, and body content using a view with a model'
        String recipient = 'fred@g2one.com'
        String mailSubject = 'Hello John'
        Map<String, String> model = [msg: 'hello']
        String expectedContent = 'Message is: hello'

        when: 'the mail service sends the message using a view'
        def message = (MimeMailMessage) mimeCapableMailService.sendMail {
            to recipient
            subject mailSubject
            body(view: '/_testemails/test', model: model)
        }

        then: 'the message should have the correct subject, content type, and content'
        message.mimeMessage.subject == mailSubject
        message.mimeMessage.contentType.startsWith('text/plain')
        message.mimeMessage.content.toString().trim() == expectedContent
    }

    void 'should send a text mail message using a view successfully'() {
        given: 'a recipient, subject, and text content using a view with a model'
        String recipient = 'fred@g2one.com'
        String mailSubject = 'Hello John'
        Map<String, String> model = [msg: 'hello']
        String expectedContent = 'Message is: hello'

        when: 'the mail service sends the text mail message using a view'
        def message = (MimeMailMessage) mimeCapableMailService.sendMail {
            to recipient
            subject mailSubject
            text(view: '/_testemails/test', model: model)
        }

        then: 'the message should have the correct content type and content'
        message.mimeMessage.contentType.startsWith('text/plain')
        message.mimeMessage.content.toString().trim() == expectedContent
    }

    void 'should send an HTML mail message using a view successfully'() {
        given: 'a recipient, subject, and HTML content using a view with a model'
        String recipient = 'fred@g2one.com'
        String mailSubject = 'Hello John'
        Map<String, String> model = [msg: 'hello']
        String expectedContent = '<b>Message is: hello</b>'

        when: 'the mail service sends the HTML message using a view'
        def message = ((MimeMailMessage) mimeCapableMailService.sendMail {
            to recipient
            subject mailSubject
            html(view: '/_testemails/testhtml', model: model)
        }).mimeMessage

        then: 'the message should have the correct subject, content type, and content'
        message.subject == mailSubject
        message.contentType.startsWith('text/html')
        message.content.toString().trim() == expectedContent
    }

    void 'should send a mail message using an HTML view successfully'() {
        given: 'a recipient, subject, and HTML content using a view with a model'
        String recipient = 'fred@g2one.com'
        String mailSubject = 'Hello John'
        Map<String, String> model = [msg: 'hello']
        String expectedContent = '<b>Message is: hello</b>'

        when: 'the mail service sends the message using an HTML view'
        def message = ((MimeMailMessage) mimeCapableMailService.sendMail {
            to recipient
            subject mailSubject
            body(view: '/_testemails/testhtml', model: model)
        }).mimeMessage

        then: 'the message should have the correct subject, content type, and content'
        message.subject == mailSubject
        message.contentType.startsWith('text/html')
        message.content.toString().trim() == expectedContent
    }

    void 'should send mail messages using a view with tags'() {
        given: 'a recipient and subject for the mail messages'
        String recipient = 'fred@g2one.com'
        String mailSubject = 'Hello John'

        when: 'the mail service sends messages with different conditions'
        def messageTrue = ((MimeMailMessage) mimeCapableMailService.sendMail {
            to recipient
            subject mailSubject
            body(view: '/_testemails/tagtest', model: [condition: true])
        }).mimeMessage
        def messageFalse = ((MimeMailMessage) mimeCapableMailService.sendMail {
            to recipient
            subject mailSubject
            body(view: '/_testemails/tagtest', model: [condition: false])
        }).mimeMessage

        then: 'the first message should reflect the true condition'
        messageTrue.subject == mailSubject
        messageTrue.content.toString().trim() == 'Condition is true'

        and: 'the second message should reflect the false condition'
        messageFalse.subject == mailSubject
        messageFalse.contentType.startsWith('text/plain')
        messageFalse.content.toString().trim() == ''
    }

    void 'should send a mail message using a view without a model successfully'() {
        given: 'a recipient and subject for the mail message'
        String recipient = 'fred@g2one.com'
        String mailSubject = 'Hello John'

        when: 'the mail service sends the message using a view without a model'
        def message = ((MimeMailMessage) mimeCapableMailService.sendMail {
            to recipient
            subject mailSubject
            body(view: '/_testemails/test')
        }).mimeMessage

        then: 'the message should have the correct subject and content type'
        message.subject == mailSubject
        message.contentType.startsWith('text/plain')

        and: 'the content of the message should be as expected'
        message.content.toString().trim() == 'Message is:'
    }

    /**
     * Tests the 'headers' feature of the mail DSL. It should add the
     * specified headers to the underlying MIME message.
     */
    void 'should send a mail message with custom headers successfully'() {
        given: 'a recipient, subject, body, and custom headers for the mail message'
        String recipient = 'fred@g2one.com'
        String mailSubject = 'Hello Fred'
        String bodyText = 'How are you?'
        Map<String, String> customHeaders = [
                'X-Mailing-List': 'user@grails.codehaus.org',
                'Sender'        : 'dilbert@somewhere.org'
        ]

        when: 'the mail service sends the message with custom headers'
        def message = mimeCapableMailService.sendMail {
            headers customHeaders
            to recipient
            subject mailSubject
            body bodyText
        }

        then: 'the message should be of type MimeMailMessage'
        message instanceof MimeMailMessage
        def msg = ((MimeMailMessage) message).mimeMessage

        and: 'the message should have the correct headers'
        msg.getHeader('X-Mailing-List')[0] == 'user@grails.codehaus.org'
        msg.getHeader('Sender')[0] == 'dilbert@somewhere.org'

        and: 'the message should have the correct recipient, subject, and content'
        msg.getRecipients(Message.RecipientType.TO)*.toString() == [recipient]
        msg.subject == mailSubject
        msg.content == bodyText
    }

    /**
     * Tests that the builder throws an exception if the user tries to
     * specify custom headers with just a plain MailSender.
     */
    void 'should throw GrailsMailException when sending mail with headers using non-MIME capable mail sender'() {
        given: 'custom headers, a recipient, subject, and body for the mail message'
        Map<String, String> mailHeaders = [
                'Content-Type': 'text/plain;charset=UTF-8',
                'Sender'      : 'dilbert@somewhere.org'
        ]
        String recipient = 'fred@g2one.com'
        String subject = 'Hello Fred'
        String bodyText = 'How are you?'

        when: 'the non-MIME capable mail service attempts to send the message with headers'
        nonMimeCapableMailService.sendMail {
            headers mailHeaders
            to recipient
            subject subject
            body bodyText
        }

        then: 'a GrailsMailException should be thrown'
        thrown GrailsMailException
    }

    void 'should send mail messages with translations based on locale'() {
        given: 'a sender, recipient, subject, and body view for both English and French locales'
        String sender = 'neur0maner@gmail.com'
        String recipient = 'neur0maner@gmail.com'
        String mailSubject = 'Hello'
        Map<String, String> model = [name: 'Luis']

        when: 'the mail service sends a message with English locale'
        def messageEn = mimeCapableMailService.sendMail {
            from sender
            to recipient
            locale 'en_US'
            subject mailSubject
            body(view: '/_testemails/i18ntest', model: model)
        }

        and: 'the mail service sends a message with French locale'
        def messageFr = mimeCapableMailService.sendMail {
            from sender
            to recipient
            locale Locale.FRENCH
            subject mailSubject
            body(view: '/_testemails/i18ntest', model: model)
        }

        then: 'the messages should be of type MimeMailMessage'
        messageEn instanceof MimeMailMessage
        messageFr instanceof MimeMailMessage

        and: 'the content should be translated correctly for both locales'
        MimeMessage msgEn = ((MimeMailMessage) messageEn).mimeMessage
        MimeMessage msgFr = ((MimeMailMessage) messageFr).mimeMessage

        def slurper = new XmlSlurper()
        def htmlEn = slurper.parseText(msgEn.content.toString())
        def htmlFr = slurper.parseText(msgFr.content.toString())

        htmlEn.body.toString() == 'Translate this: Luis'
        htmlFr.body.toString() == 'Traduis ceci: Luis'
    }

    void 'should send an email with a byte array attachment'() {
        given: 'the email details and byte array attachment'
        String recipient1 = 'fred@g2one.com'
        String recipient2 = 'ginger@g2one.com'
        String sender = 'john@g2one.com'
        String ccRecipient1 = 'marge@g2one.com'
        String ccRecipient2 = 'ed@g2one.com'
        String bccRecipient = 'joe@g2one.com'
        String subject = 'Hello John'
        String attachmentName = 'fileName'
        String attachmentContentType = 'text/plain'
        byte[] attachmentBytes = 'Hello World'.getBytes('US-ASCII')
        String htmlBody = 'this is some text'

        when: 'the mail service sends the email with a byte array attachment'
        def message = (MimeMailMessage) mimeCapableMailService.sendMail {
            multipart true
            to recipient1, recipient2
            from sender
            cc ccRecipient1, ccRecipient2
            bcc bccRecipient
            title subject
            attach attachmentName, attachmentContentType, attachmentBytes
            html htmlBody
        }
        def content = (Multipart) message.mimeMessage.content

        then: 'the email should contain the correct number of parts and attachment content'
        content.count == 2
        content.getBodyPart(1).inputStream.text == 'Hello World'
    }

    void 'should send an email with byte array and resource attachments'() {
        given: 'temporary file for resource attachment and email details'
        File tmpFile = null
        String recipient1 = 'fred@g2one.com'
        String recipient2 = 'ginger@g2one.com'
        String sender = 'john@g2one.com'
        String ccRecipient1 = 'marge@g2one.com'
        String ccRecipient2 = 'ed@g2one.com'
        String bccRecipient = 'joe@g2one.com'
        String subject = 'Hello John'
        String byteArrayAttachmentName = 'fileName'
        String byteArrayContentType = 'text/plain'
        byte[] byteArrayAttachment = 'Dear John'.getBytes('US-ASCII')
        String resourceAttachmentName = 'fileName2'
        String resourceContentType = 'application/octet-stream'
        String htmlBody = 'this is some text'

        when: 'the mail service sends the email with byte array and resource attachments'
        MailMessage message = (MimeMailMessage) mimeCapableMailService.sendMail {
            multipart true

            tmpFile = File.createTempFile('testSendmailWithAttachments', null)
            tmpFile << 'Hello World'

            to recipient1, recipient2
            from sender
            cc ccRecipient1, ccRecipient2
            bcc bccRecipient
            title subject
            attach byteArrayAttachmentName, byteArrayContentType, byteArrayAttachment
            attach resourceAttachmentName, resourceContentType, new FileSystemResource(tmpFile)
            html htmlBody
        }

        def content = (Multipart) message.mimeMessage.content

        then: 'the email should contain the correct number of parts and attachment contents'
        content.count == 3
        content.getBodyPart(1).inputStream.text == 'Dear John'
        content.getBodyPart(2).inputStream.text == 'Hello World'

        cleanup: 'delete the temporary file'
        tmpFile?.delete()
    }

    void 'should send an email with an inline attachment'() {
        given: 'the byte array of the inline attachment and email details'
        byte[] bytes = new ClassPathResource('assets/grailslogo.png').inputStream.bytes
        String recipient1 = 'fred@g2one.com'
        String recipient2 = 'ginger@g2one.com'
        String sender = 'john@g2one.com'
        String subject = 'Hello John'
        String bodyText = 'this is some text <img src="cid:abc123" />'
        String inlineContentId = 'abc123'
        String inlineContentType = 'image/png'

        when: 'the mail service sends the email with an inline attachment'
        def message = (MimeMailMessage) mimeCapableMailService.sendMail {
            multipart true
            to recipient1, recipient2
            from sender
            title subject
            text bodyText
            inline inlineContentId, inlineContentType, bytes
        }

        def inlinePart = ((MimeMultipart) ((MimeBodyPart) ((Multipart) message.mimeMessage.content).getBodyPart(0)).content).getBodyPart('<abc123>')

        then: 'the inline attachment should match the original bytes'
        inlinePart.inputStream.bytes == bytes
    }

    void 'should send an email with HTML content type'() {
        given: 'email details'
        String recipient = 'fred@g2one.com'
        String mailSubject = 'test'
        String htmlContent = '<html><head></head><body>How are you?</body></html>'

        when: 'the mail service sends the email with HTML content'
        def msg = ((MimeMailMessage) mimeCapableMailService.sendMail {
            to recipient
            subject mailSubject
            html htmlContent
        }).mimeMessage

        then: 'the email should have the correct content type and content'
        msg.contentType.startsWith('text/html')
        msg.content == htmlContent
    }

    void 'should send a multipart email with text content first and HTML content last'() {
        given: 'email details with multipart configuration'
        String recipient = 'fred@g2one.com'
        String mailSubject = 'test'
        String htmlContent = '<html><head></head><body>How are you?</body></html>'
        String textContent = 'How are you?'

        when: 'the mail service sends the email with HTML and text content'
        def msg = (MimeMailMessage) mimeCapableMailService.sendMail {
            multipart true
            to recipient
            subject mailSubject
            html htmlContent
            text textContent
        }
        def mp = (MimeMultipart) ((MimeMultipart) ((MimeMultipart) msg.mimeMessage.content).getBodyPart(0).content).getBodyPart(0).content

        then: 'the email should contain the correct number of parts and content types'
        mp.count == 2
        mp.getBodyPart(0).contentType.startsWith('text/plain')
        mp.getBodyPart(0).content == textContent
        mp.getBodyPart(1).contentType.startsWith('text/html')
        mp.getBodyPart(1).content == htmlContent
    }

    void 'should send an email in MULTIPART_MODE_RELATED mode'() {
        given: 'email details with multipart mode related'
        String recipient = 'fred@g2one.com'
        String mailSubject = 'test'
        String textContent = 'How are you?'
        String htmlContent = '<html><head></head><body>How are you?</body></html>'

        when: 'the mail service sends the email with related multipart mode'
        MimeMessage msg = ((MimeMailMessage) mimeCapableMailService.sendMail {
            multipart MimeMessageHelper.MULTIPART_MODE_RELATED
            to recipient
            subject mailSubject
            text textContent
            html htmlContent
        }).mimeMessage

        then: 'the email content should be a MimeMultipart instance'
        msg.content instanceof MimeMultipart

        and: 'the content should contain a body part'
        def content = (MimeMultipart) msg.content
        content.getBodyPart(0) instanceof MimeBodyPart

        and: 'the multipart body part should contain the expected text and HTML parts'
        def mimeBodyPart = (MimeBodyPart) content.getBodyPart(0)
        def mp = (MimeMultipart) mimeBodyPart.content
        mp.count == 2
        mp.getBodyPart(0).contentType.startsWith('text/plain')
        mp.getBodyPart(0).content == textContent
        mp.getBodyPart(1).contentType.startsWith('text/html')
        mp.getBodyPart(1).content == htmlContent
    }

    void 'should handle newlines in text GSP views'(String view) {
        given: 'a recipient, subject, and body content using a view with newlines'
        String recipient = 'abc@123.com'
        String mailSubject = 'Test subject'
        String part1 = 'Hello'
        String part2 = 'World!'

        when: 'the mail service sends the message using a view with newlines'
        def message = (SimpleMailMessage) nonMimeCapableMailService.sendMail {
            to recipient
            subject mailSubject
            text view: view, model: [part1: part1, part2: part2]
        }

        then: 'the message should have the correct content'
        message.text == "Hello\nWorld!"

        where:
        view << ['/_testemails/newLineTest', '/_testemails/newLineTagTest']
    }

    @Ignore('Not possible to get the currentResponse')
    void testContentTypeDoesNotGetChanged() {
        when:
        String originalContentType = RequestContextHolder.currentRequestAttributes().currentResponse.contentType

        MimeMailMessage message = mimeCapableMailService.sendMail {
            to 'fred@g2one.com'
            subject 'Hello John'
            body(view: '/test', model: [msg: 'hello'])
        }
        then:
        message.getMimeMessage().getSubject() == 'Hello John'
        message.mimeMessage.contentType.startsWith('text/plain') == true
        message.getMimeMessage().getContent().trim() == 'Message is: hello'
        RequestContextHolder.currentRequestAttributes().currentResponse.contentType == originalContentType
    }

//    void testViewResolutionFromPlugin() {
//        MimeMailMessage message = mimeCapableMailService.sendMail {
//            to 'fred@g2one.com'
//            subject 'Hello John'
//            body view: '/email/email', plugin: 'for-plugin-view-resolution'
//        }
//
//        message.getMimeMessage().getSubject() == 'Hello John'
//        message.mimeMessage.contentType.startsWith('text/plain') == true
//        assertEquals 'This is from a plugin!!!', message.getMimeMessage().getContent().trim()
//    }
}

class SimpleMailSender implements MailSender {
    void send(SimpleMailMessage simpleMessage) {}
    void send(SimpleMailMessage[] simpleMessages) {}
}
