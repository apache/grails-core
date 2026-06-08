/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package grails.plugin.springsecurity.rest

import grails.testing.services.ServiceUnitTest
import grails.web.mapping.LinkGenerator
import org.pac4j.oauth.client.*
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Created by Svante on 2014-10-15.
 */
class RestOauthServiceSpec extends Specification implements ServiceUnitTest<RestOauthService> {

    def setup() {
        def grailsLinkGenerator = Mock(LinkGenerator)
        1 * grailsLinkGenerator.link(_ as Map) >> "callbackUrl"
        service.grailsLinkGenerator = grailsLinkGenerator
    }

    def "it can create a client for CasOAuthWrapper"() {

        given:
        def provider = "cas"
        grailsApplication.config["grails.plugin.springsecurity.rest.oauth.${provider}.client"] = CasOAuthWrapperClient
        grailsApplication.config["grails.plugin.springsecurity.rest.oauth.${provider}.key"] = 'my_key'
        grailsApplication.config["grails.plugin.springsecurity.rest.oauth.${provider}.secret"] = 'my_secret'
        grailsApplication.config["grails.plugin.springsecurity.rest.oauth.${provider}.casOAuthUrl"] = 'cas_oauth_url'

        when:
        def client = service.getClient(provider)

        then:
        assert client instanceof CasOAuthWrapperClient
        assert client.key == grailsApplication.config["grails.plugin.springsecurity.rest.oauth.${provider}.key"]
        assert client.secret == grailsApplication.config["grails.plugin.springsecurity.rest.oauth.${provider}.secret"]
        assert client.casOAuthUrl == grailsApplication.config["grails.plugin.springsecurity.rest.oauth.${provider}.casOAuthUrl"]

    }

    @Unroll
    def "it can create a client for #provider"() {
        given:
        grailsApplication.config["grails.plugin.springsecurity.rest.oauth.${provider}.client"] = clientClass
        grailsApplication.config["grails.plugin.springsecurity.rest.oauth.${provider}.key"] = 'my_key'
        grailsApplication.config["grails.plugin.springsecurity.rest.oauth.${provider}.secret"] = 'my_secret'

        when:
        def client = service.getClient(provider)

        then:
        client.class == clientClass
        client.key == grailsApplication.config["grails.plugin.springsecurity.rest.oauth.${provider}.key"]
        client.secret == grailsApplication.config["grails.plugin.springsecurity.rest.oauth.${provider}.secret"]

        where:
        provider        | clientClass
        'dropbox'       | DropBoxClient
        'foursquare'    | FoursquareClient
        'linkedin'      | LinkedIn2Client
        'paypal'        | PayPalClient
        'twitter'       | TwitterClient
        'vk'            | VkClient
        'windowslive'   | WindowsLiveClient
        'wordpress'     | WordPressClient
        'yahoo'         | YahooClient
    }


}
