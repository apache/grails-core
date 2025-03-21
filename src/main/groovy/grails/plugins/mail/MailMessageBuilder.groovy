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

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.eclipse.angus.mail.smtp.SMTPMessage
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.InputStreamSource
import org.springframework.mail.MailMessage
import org.springframework.mail.MailSender
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMailMessage
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.util.Assert
import org.springframework.util.StringUtils

import jakarta.mail.Message
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeUtility
import java.util.concurrent.ExecutorService

/**
 * Provides a DSL style interface to mail message sending/generation.
 *
 * If the builder is constructed without a MailMessageContentRenderer, it is incapable
 * of rendering GSP views into message content.
 */
@Slf4j
@CompileStatic
class MailMessageBuilder {

    final MailSender mailSender
    final MailMessageContentRenderer mailMessageContentRenderer

    final String defaultFrom
    final String defaultTo
    final String overrideAddress

    private MailMessage message
    private MimeMessageHelper helper
    private Locale locale

    private String textContent
    private String htmlContent
    private String envelopeFrom

    private int multipart = MimeMessageHelper.MULTIPART_MODE_NO
    private boolean async = false

    private List<Inline> inlines = []

    private static class Inline {
        String id
        String contentType
        InputStreamSource toAdd
    }

    MailMessageBuilder(MailSender mailSender, MailConfigurationProperties properties,
                       MailMessageContentRenderer mailMessageContentRenderer = null) {
        this.mailSender = mailSender
        this.mailMessageContentRenderer = mailMessageContentRenderer
        this.overrideAddress = properties.overrideAddress
        this.defaultFrom = overrideAddress ?: properties.default.from
        this.defaultTo = overrideAddress ?: properties.default.to
    }

    private MailMessage getMessage() {
        if (!message) {
            if (mimeCapable) {
                helper = new MimeMessageHelper((mailSender as JavaMailSender).createMimeMessage(), multipart)
                message = new MimeMailMessage(helper)
            } else {
                message = new SimpleMailMessage()
            }
            if (defaultFrom) {
                message.from = defaultFrom
            }
            if (defaultTo) {
                message.setTo(defaultTo)
            }
        }
        return message
    }

    MailMessage sendMessage(ExecutorService executorService) {
        def message = finishMessage()

        log.trace('Sending mail {} ...', getDescription(message as Message))

        def sendingMsg = (message instanceof MimeMailMessage) ? message.mimeMessage : message

        if (envelopeFrom) {
            if (!mimeCapable) {
                throw new GrailsMailException('You must use a JavaMailSender to set the envelopeFrom.')
            }
            sendingMsg = new SMTPMessage(sendingMsg as MimeMessage)
            sendingMsg.envelopeFrom = envelopeFrom
        }

        if (async) {
            executorService.execute({
                try {
                    send(sendingMsg)
                } catch(Throwable t) {
                    log.error('Failed to send email', t)
                }
            } as Runnable)
        } else {
            send(sendingMsg)
        }

        log.trace('Sent mail {} ...', getDescription(message as Message))

        return message
    }

    /**
     * Method to send messages of any type.
     * This method is dynamically compiled to avoid the need to cast the mail sender and message to the correct types.
     * @param message Any type of message
     */
    @CompileDynamic
    private void send(Object message) {
        mailSender.send(message)
    }

    void multipart(boolean multipart) {
        this.multipart = multipart ?
                MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED :
                MimeMessageHelper.MULTIPART_MODE_NO
    }

    void multipart(int multipartMode) {
        this.multipart = multipartMode
    }

    void async(boolean async) {
        this.async = async
    }

    void headers(Map headers) {
        Assert.notEmpty(headers, 'headers cannot be null')

        // The message must be of type MimeMailMessage to add headers.
        if (!mimeCapable) {
            throw new GrailsMailException('You must use a JavaMailSender to customise the headers.')
        }

        MailMessage msg = getMessage()
        if (msg instanceof MimeMailMessage) {
            MimeMessage mimeMessage = (msg as MimeMailMessage).mimeMessageHelper.mimeMessage
            headers.each { name, value ->
                String nameString = name?.toString()
                String valueString = value?.toString()
    
                Assert.hasText(nameString, 'header names cannot be null or empty')
                Assert.hasText(valueString, "header value for '$nameString' cannot be null")
    
                mimeMessage.setHeader(nameString, valueString)
            }
        } else {
            throw new GrailsMailException('Mail message builder is not mime capable so headers cannot be set')
        }
    }

    void to(Object[] args) {
        Assert.notEmpty(args, 'to cannot be null or empty')
        Assert.noNullElements(args, 'to cannot contain null elements')
        getMessage().setTo(toDestinationAddresses(args))
    }

    void to(List args) {
        Assert.notEmpty(args, 'to cannot be null or empty')
        Assert.noNullElements(args, 'to cannot contain null elements')
        to(args.toArray())
    }

    void bcc(Object[] args) {
        Assert.notEmpty(args, 'bcc cannot be null or empty')
        Assert.noNullElements(args, 'bcc cannot contain null elements')
        getMessage().setBcc(toDestinationAddresses(args))
    }

    void bcc(List args) {
        Assert.notEmpty(args, 'bcc cannot be null or empty')
        Assert.noNullElements(args, 'bcc cannot contain null elements')
        bcc(args.toArray())
    }

    void cc(Object[] args) {
        Assert.notEmpty(args, 'cc cannot be null or empty')
        Assert.noNullElements(args, 'cc cannot contain null elements')
        getMessage().setCc(toDestinationAddresses(args))
    }

    void cc(List args) {
        Assert.notEmpty(args, 'cc cannot be null or empty')
        Assert.noNullElements(args, 'cc cannot contain null elements')
        cc(args.toArray())
    }

    void replyTo(CharSequence replyTo) {
        def value = replyTo.toString()
        Assert.hasText(value, 'replyTo cannot be null or 0 length')
        getMessage().replyTo = value
    }

    void from(CharSequence from) {
        def value = from.toString()
        Assert.hasText(value, 'from cannot be null or 0 length')
        getMessage().from = value
    }

    void envelopeFrom(CharSequence envFrom) {
        def value = envFrom.toString()
        Assert.hasText(value, 'envelope from cannot be null or 0 length')
        envelopeFrom = value
    }
    
    void title(CharSequence title) {
        Assert.notNull(title, 'title cannot be null')
        subject(title)
    }

    void subject(CharSequence title) {
        Assert.notNull(title, 'subject cannot be null')
        getMessage().subject = title.toString()
    }

    void body(CharSequence body) {
        Assert.notNull(body, 'body cannot be null')
        text(body)
    }

    void body(Map params) {
        Assert.notEmpty(params, 'body cannot be null or empty')
        def render = doRender(params)
        if (render.html) {
            html(render.out.toString()) // @todo Spring mail helper will not set correct mime type if we give it XHTML
        } else {
            text(render.out.toString())
        }
    }

    protected MailMessageContentRender doRender(Map params) {
        if (mailMessageContentRenderer == null) {
            throw new GrailsMailException('mail message builder was constructed without a message content render so cannot render views')
        }
        if (!params.view) {
            throw new GrailsMailException('no view specified')
        }
        mailMessageContentRenderer.render(new StringWriter(), params.view as String, params.model as Map, locale, params.plugin as String)
    }

    void text(Map params) {
        Assert.notEmpty(params, 'params cannot be null or empty')
        text(doRender(params).out.toString())
    }

    void text(CharSequence text) {
        Assert.notNull(text, 'text cannot be null')
        textContent = text.toString()
    }

    void html(Map params) {
        Assert.notEmpty(params, 'params cannot be null or empty')
        html(doRender(params).out.toString())
    }

    void html(CharSequence text) {
        Assert.notNull(text, 'html cannot be null')
        if (mimeCapable) {
            htmlContent = text.toString()
        } else {
            throw new GrailsMailException('mail sender is not mime capable, try configuring a JavaMailSender')
        }
    }

    void locale(String localeStr) {
        Assert.hasText(localeStr, 'locale cannot be null or empty')
        locale(StringUtils.parseLocale(localeStr))
    }

    void locale(Locale locale) {
        Assert.notNull(locale, 'locale cannot be null')
        this.locale = locale
    }

    /**
     * @deprecated use attach(String, String, byte[])
     */
    void attachBytes(String fileName, String contentType, byte[] bytes) {
        attach(fileName, contentType, bytes)
    }

    void attach(String fileName, String contentType, byte[] bytes) {
        attach(fileName, contentType, new ByteArrayResource(bytes))
    }

    void attach(File file) {
        attach(file.name, file)
    }

    void attach(String fileName, File file) {
        if (!mimeCapable) {
            throw new GrailsMailException('Message is not an instance of org.springframework.mail.javamail.MimeMessage, cannot attach bytes!')
        }
        attach(fileName, helper.fileTypeMap.getContentType(file), file)
    }

    void attach(String fileName, String contentType, File file) {
        if (!file.exists()) {
            throw new FileNotFoundException("cannot use $file as an attachment as it does not exist")
        }
        attach(fileName, contentType, new FileSystemResource(file))
    }

    void attach(String fileName, String contentType, InputStreamSource source) {
        doAdd(fileName, contentType, source, true)
    }

    void inline(String contentId, String contentType, byte[] bytes) {
        inline(contentId, contentType, new ByteArrayResource(bytes))
    }

    void inline(File file) {
        inline(file.name, file)
    }

    void inline(String fileName, File file) {
        if (!mimeCapable) {
            throw new GrailsMailException('Message is not an instance of org.springframework.mail.javamail.MimeMessage, cannot attach bytes!')
        }
        inline(fileName, helper.fileTypeMap.getContentType(file), file)
    }

    void inline(String contentId, String contentType, File file) {
        if (!file.exists()) {
            throw new FileNotFoundException("cannot use $file as an attachment as it does not exist")
        }
        inline(contentId, contentType, new FileSystemResource(file))
    }

    void inline(String contentId, String contentType, InputStreamSource source) {
        inlines << new Inline(id: contentId, contentType: contentType, toAdd: source)
    }

    protected void doAdd(String id, String contentType, InputStreamSource toAdd, boolean isAttachment) {
        if (!mimeCapable) {
            throw new GrailsMailException('Message is not an instance of org.springframework.mail.javamail.MimeMessage, cannot attach bytes!')
        }
        assert multipart, 'message is not marked as "multipart"; use "multipart true" as the first line in your builder DSL'

        getMessage() // ensure that helper is initialized
        if (isAttachment) {
            helper.addAttachment(MimeUtility.encodeWord(id), toAdd, contentType)
        } else {
            helper.addInline(MimeUtility.encodeWord(id), toAdd, contentType)
        }
    }

    boolean isMimeCapable() {
        mailSender instanceof JavaMailSender
    }

    protected String[] toDestinationAddresses(addresses) {
        if (overrideAddress) {
            addresses = addresses.collect { overrideAddress }
        }
        addresses.collect { it?.toString() } as String[]
    }

    static protected String getDescription(SimpleMailMessage message) {
        "[${message.subject}] from [${message.from}] to ${message.to}"
    }

    static protected String getDescription(Message message) {
        "[${message.subject}] from [${message.from}] to ${message.getRecipients(Message.RecipientType.TO)*.toString()}"
    }

    static protected String getDescription(MimeMailMessage message) {
        getDescription(message.mimeMessage)
    }

    MailMessage finishMessage() {
        def message = getMessage()
        if (htmlContent) {
            if (textContent) {
                helper.setText(textContent, htmlContent)
            } else {
                helper.setText(htmlContent, true)
            }
        } else {
            if (!textContent) {
                throw new GrailsMailException('message has no content, use text(), html() or body() methods to set content')
            }
            message.text = textContent
        }
        inlines.each {
            doAdd(it.id, it.contentType, it.toAdd, false)
        }
        message.sentDate = new Date()
        return message
    }
}