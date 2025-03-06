package grails.plugin.json.view.api

import grails.plugin.json.view.Book
import grails.plugin.json.view.api.internal.DefaultGrailsJsonViewHelper
import grails.plugin.json.view.api.internal.DefaultHalViewHelper
import grails.plugin.json.view.api.internal.DefaultJsonViewHelper
import grails.rest.Link
import grails.web.mapping.LinkGenerator
import spock.lang.Specification
import spock.lang.Unroll

class PaginationSpec extends Specification {
    DefaultJsonViewHelper jsonViewHelper
    LinkGenerator linkGenerator

    def setup() {
        linkGenerator = Mock()
        JsonView view = Mock() {
            getLinkGenerator() >> linkGenerator
        }
        jsonViewHelper = new DefaultJsonViewHelper(view)
    }

    void "test Pagination links without anything to paginate"() {
        when:
            List<Link> links = jsonViewHelper.getPaginationLinks(Book, total, 10, 0, null, null)

        then:
            links == []

        where:
            total << [-1, -10, 0]
    }

    @Unroll("getPaginationLinks(_, total:#total, max:#max, offset:#offset, sort:#sort, order#order) => #expectedLinks")
    void "test Pagination Links"() {
        when:
            List<Link> links = jsonViewHelper.getPaginationLinks(Book, total, max, offset, sort, order)

        then:
            linkGenerator.link(_) >> "http://example.com/book?max=$max&offset=${offset}"
            links*.rel == expectedLinks

        where:
            total | max | offset | sort | order | expectedLinks
            0     | 10  | 0      | null | null  | []
            1     | 10  | 0      | null | null  | []
            9     | 10  | 0      | null | null  | []
            10    | 10  | 0      | null | null  | []
            11    | 10  | 0      | null | null  | ['first', 'next', 'last']

            100   | 10  | 0      | null | null  | ["first", "next", "last"]
            100   | 10  | 10     | null | null  | ["first", "prev", "next", "last"]
            100   | 10  | 20     | null | null  | ["first", "prev", "next", "last"]
            100   | 10  | 89     | null | null  | ["first", "prev", "next", "last"]
            100   | 10  | 90     | null | null  | ["first", "prev", "last"]
            100   | 10  | 99     | null | null  | ["first", "prev", "last"]
            100   | 10  | 100    | null | null  | ["first", "prev", "last"]
    }

    @Unroll("getLastOffset(total:#total, max:#max) == #expectedOffset")
    void "test last offests"() {
        when:
            Integer lastOffset = jsonViewHelper.getLastOffset(total, max)

        then:
            lastOffset == expectedOffset

        where:
            total | max | expectedOffset
            0     | 5   | null
            1     | 5   | 0
            2     | 5   | 0
            3     | 5   | 0
            4     | 5   | 0
            5     | 5   | 0
            6     | 5   | 5
            99    | 5   | 95
            100   | 5   | 95
            101   | 5   | 100
    }

    @Unroll("getPrevOffset(offset:#offset, max:#max) == #expectedOffset")
    void "test prev offests"() {
        when:
            Integer prevOffset = jsonViewHelper.getPrevOffset(offset, max)

        then:
            prevOffset == expectedOffset

        where:
            offset | max | expectedOffset
            -10    | 5   | null
            0      | 5   | null
            1      | 5   | 0
            2      | 5   | 0
            3      | 5   | 0
            4      | 5   | 0
            5      | 5   | 0
            6      | 5   | 1
            99     | 5   | 94
            100    | 5   | 95
            101    | 5   | 96
    }

    @Unroll("getNextOffset(total:#total, offset:#offset, max:#max) == #expectedOffset")
    void "test getNextOffset"() {
        when:
            Integer nextOffset = jsonViewHelper.getNextOffset(total, offset, max)

        then:
            nextOffset == expectedOffset

        where:
            total | offset | max | expectedOffset
            100   | -10    | 5   | null

            100   | 0      | 5   | 5
            100   | 1      | 5   | 6
            100   | 2      | 5   | 7
            100   | 3      | 5   | 8
            100   | 4      | 5   | 9
            100   | 5      | 5   | 10
            100   | 6      | 5   | 11
            100   | 90     | 5   | 95
            100   | 94     | 5   | 99

            100   | 95     | 5   | null
            100   | 99     | 5   | null
            100   | 99     | 5   | null
            100   | 100    | 5   | null
            100   | 101    | 5   | null
            100   | 190    | 5   | null
    }
}
