package testapp1

class BootStrap {

    GreenMailService greenMailService

    def init = {
        greenMailService.start()
    }
}
