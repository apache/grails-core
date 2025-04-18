package grails.test.app

import org.grails.gorm.graphql.plugin.testing.GraphQLSpec
import grails.testing.mixin.integration.Integration
import org.grails.web.json.JSONArray
import org.grails.web.json.JSONObject
import org.spockframework.util.StringMessagePrintStream
import spock.lang.Specification
import spock.lang.Stepwise

@Integration
@Stepwise
class CommentIntegrationSpec extends Specification implements GraphQLSpec {

    void "test creating the first comment"() {
        when:
        def resp = graphQL.graphql("""
            mutation {
                commentCreate(comment: {
                    text: "First comment"
                }) {
                    id
                    text
                    replies {
                        id
                    }
                }
            }
        """)
        Map obj = resp.body().data.commentCreate

        then:
        obj.id == 1
        obj.text == 'First comment'
        obj.replies == null
    }

    void "test creating a comment that is a reply"() {
        when:
        def resp = graphQL.graphql("""
            mutation {
                commentCreate(comment: {
                    text: "Second comment"
                    parentComment: {
                        id: 1
                    }
                }) {
                    id
                    text
                    replies {
                        id
                    }
                    parentComment {
                        text
                    }
                }
            }
        """)
        Map obj = resp.body().data.commentCreate

        then:
        obj.id == 2
        obj.text == 'Second comment'
        obj.parentComment.text == 'First comment'
        obj.replies == null
    }

    void "test reading the first comment"() {
        when:
        def resp = graphQL.graphql("""
            {
                comment(id: 1) {
                    id
                    text
                    replies {
                        text
                    }
                }
            }
        """)
        Map obj = resp.body().data.comment

        then:
        obj.id == 1
        obj.text == 'First comment'
        obj.replies.size() == 1
        obj.replies[0].text == 'Second comment'
    }

    void "test querying a comment with only the parent id"() {
        given:
        PrintStream originalOut = System.out
        List<String> queries = []
        int outCount = 0
        System.setOut(new StringMessagePrintStream() {
            @Override
            protected void printed(String message) {
                queries.add(message)
                outCount++
            }
        })

        when:
        def resp = graphQL.graphql("""
            {
                comment(id: 2) {
                    parentComment {
                        id
                    }
                }
            }
        """)
        Map obj = resp.body().data.comment

        then: //The parent comment object is not queried
        obj.parentComment.id == 1
        outCount == 1
        queries[0] ==~ 'Hibernate: select this_.id as id[0-9]_[0-9]_[0-9]_, this_.version as version[0-9]_[0-9]_[0-9]_, this_.parent_comment_id as parent_c[0-9]_[0-9]_[0-9]_, this_.text as text[0-9]_[0-9]_[0-9]_ from comment this_ where this_.id=\\? limit \\?\n'

        when:
        outCount = 0
        queries = []
        resp = graphQL.graphql("""
            {
                comment(id: 2) {
                    parentComment {
                        id
                        text
                    }
                }
            }
        """)
        obj = resp.body().data.comment

        then: //The parent comment object is queried
        obj.parentComment.id == 1
        obj.parentComment.text == 'First comment'
        outCount == 1
        queries[0] ==~ 'Hibernate: select this_.id as id[0-9]_[0-9]_[0-9]_, this_.version as version[0-9]_[0-9]_[0-9]_, this_.parent_comment_id as parent_c[0-9]_[0-9]_[0-9]_, this_.text as text[0-9]_[0-9]_[0-9]_, comment2_.id as id[0-9]_[0-9]_[0-9]_, comment2_.version as version[0-9]_[0-9]_[0-9]_, comment2_.parent_comment_id as parent_c[0-9]_[0-9]_[0-9]_, comment2_.text as text[0-9]_[0-9]_[0-9]_ from comment this_ left outer join comment comment2_ on this_.parent_comment_id=comment2_.id where this_.id=\\?\n'

        cleanup:
        System.setOut(originalOut)
    }


    void "test creating a reply to a reply"() {
        when:
        def resp = graphQL.graphql("""
            mutation {
                commentCreate(comment: {
                    text: "Third comment"
                    parentComment: {
                        id: 2
                    }
                }) {
                    id
                    text
                }
            }
        """)
        Map obj = resp.body().data.commentCreate

        then:
        obj.id == 3
        obj.text == 'Third comment'

        when:
        resp = graphQL.graphql("""
            {
                comment(id: 1) {
                    id
                    text
                    replies {
                        text
                        replies {
                            text
                        }
                    }
                }
            }
        """)
        obj = resp.body().data.comment

        then:
        obj.id == 1
        obj.text == 'First comment'
        obj.replies.size() == 1
        obj.replies[0].text == 'Second comment'
        obj.replies[0].replies.size() == 1
        obj.replies[0].replies[0].text == 'Third comment'
    }

    void "test updating a comment"() {
        when:
        def resp = graphQL.graphql("""
            mutation {
                commentUpdate(id: 3, comment: {
                    parentComment: {
                        id: 1
                    }
                }) {
                    id
                    text
                }
            }
        """)
        Map obj = resp.body().data.commentUpdate

        then:
        obj.id == 3
        obj.text == 'Third comment'
    }

    void "test listing comments"() {
        when:
        def resp = graphQL.graphql("""
            {
                commentList(sort: "id") {
                    id
                    text
                    replies {
                        text
                    }
                    parentComment {
                        id
                    }
                }
            }
        """)
        List obj = resp.body().data.commentList

        then:
        obj[0].id == 1
        obj[0].text == 'First comment'
        obj[0].replies.size() == 2
        obj[0].replies.find { it.text == 'Second comment' }
        obj[0].replies.find { it.text == 'Third comment' }
        obj[0].parentComment == null

        obj[1].id == 2
        obj[1].text == 'Second comment'
        obj[1].replies == []
        obj[1].parentComment.id == 1

        obj[2].id == 3
        obj[2].text == 'Third comment'
        obj[2].replies == []
        obj[2].parentComment.id == 1
    }

    void "test deleting a comment"() {
        when:
        def resp = graphQL.graphql("""
            mutation {
                commentDelete(id: 3) {
                    success
                }
            }
        """)
        Map obj = resp.body().data.commentDelete

        then:
        obj.success
    }
}
