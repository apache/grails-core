package demo

import grails.testing.mixin.integration.Integration
import groovy.json.JsonOutput
import org.grails.gorm.graphql.plugin.testing.GraphQLSpec
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Stepwise

@Stepwise
@Integration
@IgnoreIf({ os.windows })
class SpeakerIntegrationSpec extends Specification implements GraphQLSpec {

    void "test creating a speaker"() {
        when:
        String curlCommand = '''
            // tag::curlCommand[]
curl -X "POST" "{url}" \
     -H "Content-Type: application/graphql" \
     -d $'
mutation {
  speakerCreate(speaker: {
    firstName: "James"
    lastName: "Kleeh"
  }) {
    id
    firstName
    lastName
    errors {
      field
      message
    }
  }
}'
            // end::curlCommand[]
        '''.toString().replace('{url}', getUrl())

        Process process = [ 'bash', '-c', curlCommand ].execute()
        process.waitFor()

        then:
        JsonOutput.prettyPrint(process.text) ==
        """
// tag::response[]
{
    "data": {
        "speakerCreate": {
            "id": 8,
            "firstName": "James",
            "lastName": "Kleeh",
            "errors": [
                
            ]
        }
    }
}
// end::response[]
""".replace('\n// tag::response[]\n', '')
   .replace('\n// end::response[]\n', '')

    }

    void "test updating a speaker"() {
        when:
        String curlCommand = '''
            // tag::updateCurlCommand[]
curl -X "POST" "{url}" \
     -H "Content-Type: application/graphql" \
     -d $'
mutation {
  speakerUpdate(id: 7, speaker: {
    bio: "Zachary is a member of the Grails team at OCI"
  }) {
    id
    bio
    talks {
      id
      duration
    }
    errors {
      field
      message
    }
  }
}'
            // end::updateCurlCommand[]
        '''.toString().replace('{url}', getUrl())

        Process process = [ 'bash', '-c', curlCommand ].execute()
        process.waitFor()

        then:
        JsonOutput.prettyPrint(process.text) ==
                """
// tag::updateResponse[]
{
    "data": {
        "speakerUpdate": {
            "id": 7,
            "bio": "Zachary is a member of the Grails team at OCI",
            "talks": [
                {
                    "id": 14,
                    "duration": 50
                },
                {
                    "id": 15,
                    "duration": 50
                }
            ],
            "errors": [
                
            ]
        }
    }
}
// end::updateResponse[]
""".replace('\n// tag::updateResponse[]\n', '')
                        .replace('\n// end::updateResponse[]\n', '')

    }

    void "test deleting a speaker"() {
        when:
        String curlCommand = '''
            // tag::deleteCurlCommand[]
curl -X "POST" "{url}" \
     -H "Content-Type: application/graphql" \
     -d $'
mutation {
  speakerDelete(id: 8) {
    success
    error
  }
}'
            // end::deleteCurlCommand[]
        '''.toString().replace('{url}', getUrl())

        Process process = [ 'bash', '-c', curlCommand ].execute()
        process.waitFor()

        then:
        JsonOutput.prettyPrint(process.text) ==
                """
// tag::deleteResponse[]
{
    "data": {
        "speakerDelete": {
            "success": true,
            "error": null  //<1>
        }
    }
}
// end::deleteResponse[]
""".replace('\n// tag::deleteResponse[]\n', '')
   .replace('\n// end::deleteResponse[]\n', '')
   .replace('  //<1>', '')

    }

    void "test reading a speaker"() {
        when:
        String curlCommand = '''
            // tag::readCurlCommand[]
curl -X "POST" "{url}" \
     -H "Content-Type: application/graphql" \
     -d $'
{
  speaker(id: 1) {
    firstName
    lastName
    bio
  }
}'
            // end::readCurlCommand[]
        '''.toString().replace('{url}', getUrl())

        Process process = [ 'bash', '-c', curlCommand ].execute()
        process.waitFor()

        then:
        JsonOutput.prettyPrint(process.text) ==
                """
// tag::readResponse[]
{
    "data": {
        "speaker": {
            "firstName": "Jeff Scott",
            "lastName": "Brown",
            "bio": "Jeff is a co-founder of the Grails framework, and a core member of the Grails development team."
        }
    }
}
// end::readResponse[]
""".replace('\n// tag::readResponse[]\n', '')
                        .replace('\n// end::readResponse[]\n', '')

    }

    void "test fetch speaker's list"() {
        when:
        String curlCommand = '''
            // tag::listCurlCommand[]
curl -X "POST" "{url}" \
     -H "Content-Type: application/graphql" \
     -d $'
{
  speakerList(max: 3) {
    id
    name
    talks {
      title
    }  
  }
}'
            // end::listCurlCommand[]
        '''.toString().replace('{url}', getUrl())
        Process process = [ 'bash', '-c', curlCommand ].execute()
        process.waitFor()
        then:
        JsonOutput.prettyPrint(process.text) ==
                """
// tag::listResponse[]
{
    "data": {
        "speakerList": [
            {
                "id": 1,
                "name": "Jeff Scott Brown",
                "talks": [
                    {
                        "title": "Polyglot Web Development with Grails 3"
                    },
                    {
                        "title": "REST With Grails 3"
                    },
                    {
                        "title": "Testing in Grails 3"
                    }
                ]
            },
            {
                "id": 2,
                "name": "Graeme Rocher",
                "talks": [
                    {
                        "title": "What's New in Grails?"
                    },
                    {
                        "title": "The Latest and Greatest in GORM"
                    }
                ]
            },
            {
                "id": 3,
                "name": "Paul King",
                "talks": [
                    {
                        "title": "Groovy: The Awesome Parts"
                    }
                ]
            }
        ]
    }
}
// end::listResponse[]
""".replace('\n// tag::listResponse[]\n', '')
                        .replace('\n// end::listResponse[]\n', '')

    }

    void "test fetch speaker count"() {
        when:
        String curlCommand = '''
            // tag::countCurlCommand[]
curl -X "POST" "{url}" \
     -H "Content-Type: application/graphql" \
     -d $'
{
  speakerCount
}'
            // end::countCurlCommand[]
        '''.toString().replace('{url}', getUrl())
        Process process = [ 'bash', '-c', curlCommand ].execute()
        process.waitFor()
        then:
        JsonOutput.prettyPrint(process.text) ==
                """
// tag::countResponse[]
{
    "data": {
        "speakerCount": 7
    }
}
// end::countResponse[]
""".replace('\n// tag::countResponse[]\n', '')
                        .replace('\n// end::countResponse[]\n', '')

    }
}
