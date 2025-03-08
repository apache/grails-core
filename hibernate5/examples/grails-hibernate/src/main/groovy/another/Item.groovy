package another

import grails.artefact.Artefact
import org.grails.core.artefact.DomainClassArtefactHandler

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id

/**
 * Created by graemerocher on 27/01/2017.
 */
@Entity
@Artefact(DomainClassArtefactHandler.TYPE)
class Item {
    @Id
    @GeneratedValue
    Long id

    String name
}
