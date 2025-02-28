package grails.events.annotation

import org.grails.datastore.gorm.transactions.transform.TransactionalTransform
import org.grails.datastore.mapping.core.order.OrderedComparator
import org.grails.events.transform.PublisherTransform
import spock.lang.Specification

/**
 * Created by graemerocher on 30/03/2017.
 */
class PublisherTransformSpec extends Specification {

    void 'Test order'() {

        given: 'a list of transforms'
            def list = [new PublisherTransform(), new TransactionalTransform()]

        when: 'we sort the list'
            Collections.sort(list, new OrderedComparator<>())

        then: 'the transactional transform is first'
            list.first() instanceof TransactionalTransform
    }
}
