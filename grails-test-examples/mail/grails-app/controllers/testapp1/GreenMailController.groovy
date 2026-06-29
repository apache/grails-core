package testapp1

import com.icegreen.greenmail.util.GreenMailUtil

class GreenMailController {

    GreenMailService greenMailService

    def index() {
        def greenMail = greenMailService.greenMail
        if (!greenMail) {
            render 'GreenMail is not running'
            return
        }
        def messages = greenMail.receivedMessages.collect { msg ->
            [
                    from   : msg.from.address,
                    to     : msg.allRecipients.join(', '),
                    subject: msg.subject,
                    body   : GreenMailUtil.getBody(msg)
            ]
        }
        render(view: 'greenMail', model: [messages: messages])
    }
}
