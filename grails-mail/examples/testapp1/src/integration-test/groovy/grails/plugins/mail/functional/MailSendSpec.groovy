package grails.plugins.mail.functional

import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration
import testapp1.GreenMailService

@Integration
class MailSendSpec extends ContainerGebSpec {

    GreenMailService greenMailService

    def cleanup() {
        greenMailService.reset()
    }

    void 'controller should be able to send mail'() {
        given:
        def mailDetails = [
            from: 'abc@123.com',
            to: '123@abc.com',
            subject: 'Test subject',
            text: 'Test body'
        ]

        when:
        go "/sendMail?${toParams(mailDetails)}"

        then:
        pageSource.contains('123@abc.com')
        pageSource.contains('abc@123.com')
        pageSource.contains('Test subject')
        pageSource.contains('Test body')
    }

    private static String toParams(Map params) {
        params.collect { k, v ->
            "${URLEncoder.encode(k.toString(), 'UTF-8')}=${URLEncoder.encode(v.toString(), 'UTF-8')}"
        }.join('&')
    }
}
