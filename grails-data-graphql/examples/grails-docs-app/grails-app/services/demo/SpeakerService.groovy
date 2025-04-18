package demo

import grails.gorm.transactions.Transactional
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

@CompileStatic
@Slf4j
class SpeakerService {

    @Transactional
    Speaker save(String firstName, String lastName, String email, String bio, List<Talk> talks) {
        Speaker speaker = new Speaker(firstName: firstName,
                lastName: lastName,
                email: email,
                bio: bio)
        for (Talk talk : talks) {
            speaker.addToTalks(talk)
        }
        if (!speaker.save()) {
            log.error "Error while saving speaker"
        }
        speaker
    }
}