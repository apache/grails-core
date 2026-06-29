package testapp1

class SendMailController {

    def mailService

    def index() {
        mailService.sendMail {
            to params.to
            from params.from
            subject params.subject
            text params.text
        }
        redirect(controller: 'greenMail', action: 'index')
    }
}
