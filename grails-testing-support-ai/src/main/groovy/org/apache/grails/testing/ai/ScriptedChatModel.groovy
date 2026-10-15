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

import java.util.concurrent.ConcurrentLinkedDeque
import java.util.function.Function

import groovy.transform.CompileStatic
import reactor.core.publisher.Flux

import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.chat.prompt.Prompt

/**
 * A chat model that answers with scripted responses instead of calling a provider, and records every
 * prompt it receives, so a test can drive the code that uses a {@code ChatClient} and assert on what it
 * asked.
 *
 * <p>Responses are consumed in the order they were scripted; a response may be fixed text or a function
 * of the prompt. When none is left, the model echoes the text of the last user message, so a test that
 * does not care about the answer still gets one; a {@link #setStrict strict} model fails instead, for a
 * test that must not run out of script unnoticed. A streamed call answers in chunks of one word each,
 * so code that assembles a stream is exercised as it would be against a real model. The answer of a
 * streamed call is taken when the {@code Flux} is subscribed, not when it is created, so a stream that
 * is never subscribed consumes nothing and a retried one answers afresh.</p>
 *
 * @since 8.1
 */
@CompileStatic
class ScriptedChatModel implements ChatModel {

    private final Deque<Function<Prompt, String>> responses = new ConcurrentLinkedDeque<>()
    private final List<Prompt> prompts = Collections.synchronizedList(new ArrayList<Prompt>())
    private volatile boolean strict

    /**
     * Scripts the next responses, in order.
     */
    ScriptedChatModel respondWith(String... texts) {
        for (String text : texts) {
            String answer = text // a variable of its own per iteration, so each responder keeps its own text
            responses.add((Prompt prompt) -> answer)
        }
        this
    }

    /**
     * Scripts the next response as a function of the prompt it answers.
     */
    ScriptedChatModel respondWith(Function<Prompt, String> responder) {
        responses.add(responder)
        this
    }

    /**
     * Whether a prompt that arrives when no scripted response is left is a failure rather than echoed.
     * {@link #reset()} turns it off again, so a test that sets it affects no other.
     */
    void setStrict(boolean strict) {
        this.strict = strict
    }

    boolean isStrict() {
        strict
    }

    /**
     * @return every prompt received so far, oldest first
     */
    List<Prompt> getPrompts() {
        List.copyOf(prompts)
    }

    /**
     * @return the prompt received last, or {@code null} when none was
     */
    Prompt getLastPrompt() {
        synchronized (prompts) {
            prompts ? prompts.last() : null
        }
    }

    /**
     * @return the text of the last user message of the last prompt, or {@code null} when none was received
     */
    String getLastUserText() {
        lastPrompt?.userMessage?.text
    }

    /**
     * Forgets the scripted responses and the received prompts, and turns strictness off.
     */
    void reset() {
        responses.clear()
        prompts.clear()
        strict = false
    }

    @Override
    ChatResponse call(Prompt prompt) {
        response(answer(prompt))
    }

    @Override
    Flux<ChatResponse> stream(Prompt prompt) {
        Flux.defer { -> Flux.fromIterable(chunk(answer(prompt))) }.map { String chunkText -> response(chunkText) }
    }

    @Override
    ChatOptions getOptions() {
        ChatOptions.builder().build()
    }

    /**
     * Splits text into the chunks a streamed answer arrives in: one per word, each keeping the whitespace
     * that follows it, so the chunks joined are the text.
     */
    static List<String> chunk(String text) {
        text.split('(?<=\\s)').toList()
    }

    /**
     * Records the prompt and takes the response that answers it as one step, so concurrent calls pair
     * each prompt with the response scripted for its turn.
     */
    private String answer(Prompt prompt) {
        Function<Prompt, String> responder
        synchronized (prompts) {
            prompts.add(prompt)
            responder = responses.poll()
        }
        if (responder != null) {
            return responder.apply(prompt)
        }
        if (strict) {
            throw new IllegalStateException("No scripted response is left for prompt ${prompts.size()}: ${prompt.userMessage?.text}")
        }
        prompt.userMessage.text ?: ''
    }

    private static ChatResponse response(String text) {
        new ChatResponse([new Generation(new AssistantMessage(text))])
    }

}
