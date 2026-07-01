/*
 * Copyright 2008 the original author or authors.
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

import org.grails.testing.GrailsUnitTest
import org.springframework.mail.MailSender
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.web.context.support.ServletContextResource
import spock.lang.Issue
import spock.lang.Specification

import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import jakarta.mail.internet.MimeUtility
import jakarta.servlet.ServletContext

/**
 * Test case for {@link MailMessageBuilder}.
 */
class MailMessageBuilderSpec extends Specification implements GrailsUnitTest {

	MailMessageBuilder testJavaMailSenderBuilder
	MailMessageBuilder testBasicMailSenderBuilder

	private static String defaultFrom = "from@grailsplugin.com"
	private static String defaultTo = "to@grailsplugin.com"

	def setup() {
		def properties = new MailConfigurationProperties()
		properties.default.from = defaultFrom
		properties.default.to = defaultTo

		def mockJavaMailSender = Stub(JavaMailSender) {
			createMimeMessage() >> new MimeMessage(Session.getInstance(new Properties()))
		}

		testJavaMailSenderBuilder = new MailMessageBuilder(mockJavaMailSender, properties)

		def mockBasicMailSender = Stub(MailSender)
		testBasicMailSenderBuilder = new MailMessageBuilder(mockBasicMailSender, properties)
	}

	/**
	 * .
	 */
	void "Tests the basic elements of the mail DSL"() {
		when:
		processDsl {
			to "fred@g2one.com"
			subject "Hello Fred"
			body 'How are you?'
		}

		then:
		MimeMessage msg = testJavaMailSenderBuilder.message.mimeMessage
		to(msg).size() == 1
		to(msg)[0].toString() == "fred@g2one.com"
		msg.subject == "Hello Fred"
		msg.content == "How are you?"
	}

	void "Tests that multiple recipients are added to the underlying mail message correctly"() {
		when:
		processDsl {
			to "fred@g2one.com", "ginger@g2one.com", "grace@hollywood.com"
			from "john@g2one.com"
			cc "marge@g2one.com", "ed@g2one.com"
			bcc "joe@g2one.com"
			subject "Hello John"
			body 'this is some text'
		}

		then:
		MimeMessage msg = testJavaMailSenderBuilder.message.mimeMessage
		to(msg) == ["fred@g2one.com", "ginger@g2one.com", "grace@hollywood.com"]
		cc(msg) == ["marge@g2one.com", "ed@g2one.com"]
		bcc(msg) == ["joe@g2one.com"]
		msg.from.size() == 1
		msg.from[0].toString() == "john@g2one.com"
		msg.subject == "Hello John"
		msg.content == "this is some text"
	}

	void "Tests the 'headers' feature of the mail DSL. It should add the specified headers to the underlying MIME message"() {
		when:
		processDsl {
			headers "X-Mailing-List": "user@grails.codehaus.org",
					"Sender": "dilbert@somewhere.org"
			to "fred@g2one.com"
			subject "Hello Fred"
			body 'How are you?'
		}

		then:
		MimeMessage msg = testJavaMailSenderBuilder.message.mimeMessage
		msg.getHeader("X-Mailing-List", ", ") == "user@grails.codehaus.org"
		msg.getHeader("Sender", ", ") == "dilbert@somewhere.org"
		to(msg) == ["fred@g2one.com"]
		msg.subject == "Hello Fred"
		msg.content == "How are you?"
	}

	void "Tests that the builder throws an exception if the user tries to specify custom headers with just a plain MailSender"() {
		when:
		processDsl(testBasicMailSenderBuilder) {
			headers "Content-Type": "text/plain;charset=UTF-8",
					"Sender": "dilbert@somewhere.org"
			to "fred@g2one.com"
			subject "Hello Fred"
			body 'How are you?'
		}

		then:
		thrown(GrailsMailException)
	}

	void "Test that default to and from fields works as expected"() {
		when:
		processDsl {
			subject "Hello Fred"
			body 'How are you?'
		}
		then:
		MimeMessage msg = testJavaMailSenderBuilder.message.mimeMessage
		to(msg)[0].toString() == defaultTo
		msg.from[0].toString() == defaultFrom
	}

	void "Test that envelopeFrom works as expected"() {
		when:
		processDsl {
			to "fred@g2one.com"
			from "john@g2one.com"
			envelopeFrom "peter@g2one.com"
			subject "Hello Fred"
			body 'How are you?'
		}
		then:
		MimeMessage msg = testJavaMailSenderBuilder.message.mimeMessage
		to(msg)[0].toString() == "fred@g2one.com"
		msg.from[0].toString() == "john@g2one.com"
		testJavaMailSenderBuilder.envelopeFrom == "peter@g2one.com"
	}

	void "Test that attachments works as expected"() {
		when:
		processDsl {
			multipart true
			to "fred@g2one.com"
			subject "Hello Fred"
			body 'How are you?'
			attachBytes "dummy.bin", "application/binary", "abcdef".bytes
			attachBytes "äöü.bin", "application/binary", "abcdef".bytes
		}

		then:
		MimeMessage msg = testJavaMailSenderBuilder.message.mimeMessage
		msg.content instanceof MimeMultipart
		3 == msg.content.count

		and:
		def attachment1 = msg.content.getBodyPart(1)
		attachment1.content.text == "abcdef"
		attachment1.fileName == "dummy.bin"

		and:
		def attachment2 = msg.content.getBodyPart(2)
		attachment2.fileName == MimeUtility.encodeWord("äöü.bin")
	}

	void "Test that attaching a stream works as expected"() {
		setup:
		def servletContext = [getResourceAsStream: { new ByteArrayInputStream("abcdef".bytes) }] as ServletContext

		when:
		processDsl {
			multipart true
			to "fred@g2one.com"
			subject "Hello Fred"
			body 'How are you?'
			attach "dummy.bin", "application/binary", new ServletContextResource(servletContext, "path/to/file")
		}

		then:
		def msg = testJavaMailSenderBuilder.message.mimeMessage
		msg.content instanceof MimeMultipart
		msg.content.count == 2

		def attachment = msg.content.getBodyPart(1)
		attachment.content.text == "abcdef"
		attachment.fileName == "dummy.bin"
	}

	void "Test that attachment using attach file no filename override"() {
		setup: "create temp file to attach"
		def tempFile = File.createTempFile("grailsMailUnitTest", ".txt")
		tempFile << 'abcdef'

		when:
		processDsl {
			multipart true
			to "fred@g2one.com"
			subject "Hello Fred"
			body 'How are you?'
			attach tempFile
		}
		then:

		def msg = testJavaMailSenderBuilder.message.mimeMessage
		msg.content instanceof MimeMultipart
		msg.content.count == 2

		def attachment = msg.content.getBodyPart(1)
		attachment.content == 'abcdef'
		attachment.fileName == tempFile.name
		attachment.contentType == 'text/plain'

		cleanup:
		tempFile.delete()
	}

	void "Test attachment using attach file with filename override"() {
		setup:
		def tempFile = File.createTempFile("grailsMailUnitTest", ".txt")
		tempFile << 'abcdef'

		when:
		processDsl {
			multipart true
			to "fred@g2one.com"
			subject "Hello Fred"
			body 'How are you?'
			attach 'alternativeName.txt', tempFile
		}
		then:
		def msg = testJavaMailSenderBuilder.message.mimeMessage
		msg.content instanceof MimeMultipart
		msg.content.count == 2

		def attachment = msg.content.getBodyPart(1)
		attachment.content == 'abcdef'
		attachment.fileName == 'alternativeName.txt'
		attachment.contentType == 'text/plain'

		cleanup:
		tempFile.delete()
	}

	void "Test Attach File With Non Existent File"() {
		when:
		processDsl {
			multipart true
			to "fred@g2one.com"
			subject "Hello Fred"
			body 'How are you?'
			attach new File("I don't exist.zip")
		}
		then:
		thrown(FileNotFoundException)
	}

	@Issue("for issue GPMAIL-60")
	void "test Attach Call In Beginning Of Dsl"() {
		setup:
		def servletContext = [getResourceAsStream: { new ByteArrayInputStream("abcdef".bytes) }] as ServletContext

		when:
		processDsl {
			multipart true
			attach "dummy.bin", "application/binary", new ServletContextResource(servletContext, "path/to/file")
			to "fred@g2one.com"
			subject "Hello Fred"
			body 'How are you?'
		}
		then:
		def msg = testJavaMailSenderBuilder.message.mimeMessage
		msg.content instanceof MimeMultipart
		msg.content.count == 2
		msg.getHeader("To", " ") == "fred@g2one.com"
		msg.getHeader("Subject", " ") == "Hello Fred"

		and:
		def attachment = msg.content.getBodyPart(1)
		attachment.content.text == "abcdef"
		attachment.fileName == "dummy.bin"
	}

	private List to(MimeMessage msg) {
		msg.getRecipients(Message.RecipientType.TO)*.toString()
	}

	private List cc(MimeMessage msg) {
		msg.getRecipients(Message.RecipientType.CC)*.toString()
	}

	private List bcc(MimeMessage msg) {
		msg.getRecipients(Message.RecipientType.BCC)*.toString()
	}

	private processDsl(Closure c) {
		processDsl(testJavaMailSenderBuilder, c)
	}

	private processDsl(MailMessageBuilder builder, Closure c) {
		c.delegate = builder
		c.call()
		builder.finishMessage()
	}
}
