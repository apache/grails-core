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
class AuthorIntegrationSpec extends Specification implements GraphQLSpec {

    void "test creating an author"() {
        when:
        String curlCommand = '''
            // tag::createCommand[]
curl -X "POST" "{url}" \
     -H "Content-Type: application/graphql" \
     -d $'
mutation {
  authorCreate(author: {
    name: "Sally",
    homeLocation: {
      lat: "41.101539",
      long: "-80.653381"
    },
    books: [
      {key: "0307887448", value: {title: "Ready Player One"}},
      {key: "0743264746", value: {title: "Einstein: His Life and Universe"}}
    ]
  }) {
    id
    name
    homeLocation {
      lat
      long
    }
    books {
      key
      value {
        id
        title
      }
    }  
    errors {
      field
      message
    }
  }
}'
            // end::createCommand[]
        '''.toString().replace('{url}', getUrl())

        Process process = [ 'bash', '-c', curlCommand ].execute()
        process.waitFor()

        then:
        JsonOutput.prettyPrint(process.text) ==
                """
// tag::createResponse[]
{
    "data": {
        "authorCreate": {
            "id": 1,
            "name": "Sally",
            "homeLocation": {
                "lat": "41.101539",
                "long": "-80.653381"
            },
            "books": [
                {
                    "key": "0743264746",
                    "value": {
                        "id": 1,
                        "title": "Einstein: His Life and Universe"
                    }
                },
                {
                    "key": "0307887448",
                    "value": {
                        "id": 2,
                        "title": "Ready Player One"
                    }
                }
            ],
            "errors": [
                
            ]
        }
    }
}
// end::createResponse[]
""".replace('\n// tag::createResponse[]\n', '')
   .replace('\n// end::createResponse[]\n', '')

    }
}
