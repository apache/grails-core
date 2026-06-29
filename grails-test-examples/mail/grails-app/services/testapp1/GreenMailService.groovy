package testapp1

import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest
import groovy.util.logging.Slf4j
import jakarta.annotation.PreDestroy

@Slf4j
class GreenMailService {

    GreenMail greenMail

    void start() {
        greenMail = new GreenMail(ServerSetupTest.SMTP)
        greenMail.start()
        log.info('Greenmail started on port {}', greenMail.getSmtp().getPort())
    }

    void reset() {
        if (greenMail) {
            greenMail.reset()
            log.debug('Greenmail reset')
        }
    }

    @PreDestroy
    void stop() {
        if (greenMail) {
            greenMail.stop()
            log.info('Greenmail stopped')
        }
    }
}