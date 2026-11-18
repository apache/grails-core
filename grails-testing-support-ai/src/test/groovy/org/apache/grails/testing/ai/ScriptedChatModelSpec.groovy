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
package org.apache.grails.testing.ai

import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

import reactor.core.publisher.Flux

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.prompt.Prompt

import spock.lang.Specification

class ScriptedChatModelSpec extends Specification {

    ScriptedChatModel model = new ScriptedChatModel()

    void 'answers with the scripted responses in order, then echoes the user message'() {
        given:
        model.respondWith('first', 'second')

        expect:
        model.call(new Prompt('one')).result.output.text == 'first'
        model.call(new Prompt('two')).result.output.text == 'second'
        model.call(new Prompt('three')).result.output.text == 'three'
    }

    void 'a scripted responder sees the prompt it answers'() {
        given:
        model.respondWith { Prompt prompt -> "You said: ${prompt.userMessage.text}".toString() }

        expect:
        model.call(new Prompt('hi')).result.output.text == 'You said: hi'
    }

    void 'records every prompt it receives'() {
        when:
        model.call(new Prompt('a'))
        model.stream(new Prompt('b')).blockLast()

        then:
        model.prompts*.contents == ['a', 'b']
        model.lastPrompt.userMessage.text == 'b'
        model.lastUserText == 'b'
    }

    void 'has received nothing to begin with'() {
        expect:
        model.prompts.empty
        model.lastPrompt == null
        model.lastUserText == null
    }

    void 'streams the answer a word at a time'() {
        given:
        model.respondWith('Hello streaming world')

        when:
        List<String> chunks = model.stream(new Prompt('x')).map { it.result.output.text }.collectList().block()

        then:
        chunks == ['Hello ', 'streaming ', 'world']
    }

    void 'the chunks of a streamed answer keep its whitespace'() {
        expect:
        ScriptedChatModel.chunk(text) == chunks
        chunks.join('') == text

        where:
        text       | chunks
        'one'      | ['one']
        'a  b\nc'  | ['a ', ' ', 'b\n', 'c']
        ''         | ['']
    }

    void 'answers a chat client, synchronously and streamed'() {
        given:
        model.respondWith('42')
        ChatClient chatClient = ChatClient.create(model)

        expect:
        chatClient.prompt().user('What is six times seven?').call().content() == '42'
        model.lastUserText == 'What is six times seven?'
        chatClient.prompt().user('again').stream().content().collectList().block() == ['again']
    }

    void 'a streamed answer is taken when the stream is subscribed, not when it is created'() {
        given:
        model.respondWith('first', 'second')

        when: 'a stream is created but never subscribed'
        model.stream(new Prompt('unsubscribed'))

        then: 'nothing was recorded or consumed'
        model.prompts.empty
        model.call(new Prompt('one')).result.output.text == 'first'
    }

    void 'a retried stream answers afresh instead of replaying the first answer'() {
        given:
        model.respondWith('first', 'second')
        int attempts = 0

        when:
        List<String> chunks = model.stream(new Prompt('x'))
                .doOnSubscribe { attempts++ }
                .flatMap { response -> attempts == 1 ? Flux.error(new IllegalStateException('flaky')) : Flux.just(response.result.output.text) }
                .retry(1)
                .collectList()
                .block()

        then:
        chunks == ['second']
        model.prompts*.contents == ['x', 'x']
    }

    void 'concurrent calls pair each recorded prompt with the response scripted for its turn'() {
        given: 'responders that record the prompt they answer'
        int calls = 200
        List<Prompt> answered = Collections.synchronizedList(new ArrayList<Prompt>(Collections.nCopies(calls, (Prompt) null)))
        (0..<calls).each { int turn ->
            model.respondWith { Prompt prompt ->
                answered[turn] = prompt
                "answer ${turn}".toString()
            }
        }
        ExecutorService executor = Executors.newFixedThreadPool(8)

        when:
        List<Future<String>> answers = (1..calls).collect { int call ->
            executor.submit({ -> model.call(new Prompt("prompt ${call}".toString())).result.output.text } as Callable<String>)
        }
        List<String> texts = answers*.get()
        executor.shutdown()

        then: 'the prompt recorded at each position is the one the responder for that position answered'
        texts.toSet() == (0..<calls).collect { "answer ${it}".toString() }.toSet()
        model.prompts.size() == calls
        (0..<calls).every { int turn -> model.prompts[turn].is(answered[turn]) }
    }

    void 'a strict model fails when the script runs out instead of echoing'() {
        given:
        model.strict = true
        model.respondWith('only')

        expect:
        model.call(new Prompt('one')).result.output.text == 'only'

        when:
        model.call(new Prompt('two'))

        then:
        IllegalStateException failure = thrown()
        failure.message.contains('prompt 2')
        failure.message.contains('two')
        model.prompts*.contents == ['one', 'two']
    }

    void 'a strict model fails on a streamed call too'() {
        given:
        model.strict = true

        when:
        model.stream(new Prompt('nothing scripted')).blockLast()

        then:
        thrown(IllegalStateException)
    }

    void 'reset turns strictness off, so a test that set it affects no other'() {
        given:
        model.strict = true

        when:
        model.reset()

        then:
        !model.strict
        model.call(new Prompt('echoed again')).result.output.text == 'echoed again'
    }

    void 'reset forgets the script and the prompts'() {
        given:
        model.respondWith('scripted')
        model.call(new Prompt('before'))

        when:
        model.reset()

        then:
        model.prompts.empty
        model.call(new Prompt('after')).result.output.text == 'after'
    }

}
