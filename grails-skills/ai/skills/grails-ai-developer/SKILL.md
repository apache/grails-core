---
name: grails-ai-developer
description: Guide for using AI models from a Grails application through the grails-spring-ai plugin and Spring AI, covering provider starters and OpenAI-compatible services, the ChatClient in services, structured output, tool calling, answers streamed from controller actions as server-sent events, the configuration defaults the plugin ships, and unit, integration and functional tests with scripted models instead of a provider
license: Apache-2.0
---

<!--
SPDX-License-Identifier: Apache-2.0

Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements; and to You under the Apache License, Version 2.0.
-->

## What I Do

- Add Spring AI to a Grails 8.1 or later application through the `grails-spring-ai` plugin, with the provider the application needs.
- Write services that ask a model through the `ChatClient`, map answers onto classes, and let the model call the application's own services as tools.
- Stream a model's answer from a controller action to the browser as server-sent events, which Grails does for any Reactive Streams `Publisher` an action returns.
- Configure retries, logging, provider selection and streaming timeouts the Grails way, per environment.
- Test the code that uses a model without a provider, credentials or network, with the scripted chat model and the stub embedding model of `grails-testing-support-ai`.

This skill is about using AI *from* an application. It is not about AI coding agents working *on* a Grails application; that is the `grails-developer` skill.

## When to Use Me

Activate this skill when:

- Adding a chat, summarisation, classification, extraction or semantic search feature to a Grails application.
- Choosing between Spring AI providers, or pointing the OpenAI starter at an OpenAI-compatible service.
- A controller should stream a model's answer token by token.
- Writing or fixing tests for code that injects `ChatClient.Builder`, `ChatModel`, `EmbeddingModel` or a `VectorStore`.
- A Grails application that uses Spring AI fails to start in the test environment, or asks for an API key it should not need.

## Primary Sources

Use the published documentation before changing an application. `<version>` stands for the application's Grails version, such as the `grailsVersion` in its `gradle.properties`; use `snapshot` for a snapshot version.

| URL | Use for |
|-----|---------|
| `https://grails.apache.org/docs/<version>/guide/usingAi.html` | The Grails chapter: installation, model providers, configuration, chat client, streaming, testing |
| `https://grails.apache.org/docs/<version>/guide/async.html#asyncStreaming` | Streaming any `Publisher` from an action, the event format, renderers, timeouts |
| `https://docs.spring.io/spring-ai/reference/2.0/` | The Spring AI reference: the `ChatClient` API, advisors, tools, memory, vector stores, MCP, and one page per provider |
| `https://grails.apache.org/docs/<version>/ref/Configuration/Application%20Properties.html` | Every `grails.async.streaming.*` and `grails.ai.stub.*` setting with its default |

The Spring AI version is the one the Grails BOM manages; read `https://grails.apache.org/docs/<version>/ref/Versions/Grails%20BOM.html` to see it, and use the matching Spring AI reference line.

## Setup

The plugin is provider-neutral: it brings the `ChatClient` and the auto-configuration every provider shares, but no model. The application adds exactly one starter per kind of model it uses, and the Grails BOM supplies every version:

```groovy
// build.gradle
dependencies {
    implementation 'org.apache.grails:grails-spring-ai'
    implementation 'org.springframework.ai:spring-ai-starter-model-openai' // or another provider's starter
    testImplementation 'org.apache.grails:grails-testing-support-ai'
}
```

Credentials come from the environment, never from a committed file: Spring AI reads `OPENAI_API_KEY` or `ANTHROPIC_API_KEY` when `spring.ai.<provider>.api-key` is not set. Never write a placeholder such as `${OPENAI_API_KEY}` into `application.yml`: when the variable is unset the placeholder itself is sent as the key, and the call fails with a 401 instead of a clear error.

A new application gets the same from the `grails-spring-ai` feature, or from `spring-ai-openai`, `spring-ai-anthropic` or `spring-ai-ollama`, which add the provider as well:

```shell
grails -t forge create-app my-app --features spring-ai-openai
```

Do not add a second provider starter "just in case": every provider auto-configures each kind of model it offers, and two chat models make the `ChatClient.Builder` ambiguous until `spring.ai.model.chat` names one.

## Choosing a Provider

| Need | Starter | Property it needs |
|------|---------|-------------------|
| OpenAI | `spring-ai-starter-model-openai` | `spring.ai.openai.api-key` |
| Anthropic | `spring-ai-starter-model-anthropic` | `spring.ai.anthropic.api-key` |
| Local models, no credentials | `spring-ai-starter-model-ollama` | `spring.ai.ollama.base-url`, a running Ollama |
| Google Gemini | `spring-ai-starter-model-google-genai` | `spring.ai.google.genai.api-key` |
| Amazon Bedrock | `spring-ai-starter-model-bedrock-converse` | AWS credentials and `spring.ai.bedrock.aws.region` |
| Mistral AI | `spring-ai-starter-model-mistral-ai` | `spring.ai.mistralai.api-key` |
| DeepSeek | `spring-ai-starter-model-deepseek` | `spring.ai.deepseek.api-key` |

Groq, NVIDIA, Perplexity, Azure OpenAI, Docker Model Runner, vLLM and LM Studio speak the OpenAI API. Use the OpenAI starter with a base URL, the service's key, and the model name; a local server that needs no key takes an empty one:

```yaml
spring:
    ai:
        openai:
            base-url: https://api.groq.com/openai/v1
            api-key: ${GROQ_API_KEY}
            chat:
                model: llama-3.3-70b-versatile
```

Prefer a provider's own starter when the application needs what only it offers: Anthropic's prompt caching and PDF input, Bedrock's IAM credentials, Gemini's audio and video input, Ollama's model pulling.

The model and its options live under the provider's prefix, for example `spring.ai.openai.chat.model` and `spring.ai.openai.chat.temperature`. With more than one provider on the classpath, `spring.ai.model.chat`, `spring.ai.model.embedding`, `spring.ai.model.image`, `spring.ai.model.audio.transcription`, `spring.ai.model.audio.speech` and `spring.ai.model.moderation` name the provider per kind; `none` turns a kind off.

## The Chat Client in a Service

Inject the auto-configured `ChatClient.Builder` and build the client once; the builder is a prototype, so each service can carry its own defaults:

```groovy
package my.app

import org.springframework.ai.chat.client.ChatClient

import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
class AnswerService {

    ChatClient chatClient

    AnswerService(ChatClient.Builder chatClientBuilder) {
        chatClient = chatClientBuilder
                .defaultSystem('You answer questions about the Grails framework, briefly.')
                .build()
    }

    String answer(String question) {
        chatClient.prompt()
                .user(question)
                .call()
                .content()
    }
}
```

Groovy closures coerce to the consumers the fluent API takes, so `.user { it.text('Summarise {text}').param('text', text) }` works as in Java.

Map an answer onto a class with `entity()`; Spring AI derives the JSON schema, instructs the model and converts the reply. Add `spec -> spec.validateSchema()` for a self-correcting retry on malformed JSON:

```groovy
class Summary {
    String title
    List<String> keyPoints
}

Summary summarise(String text) {
    chatClient.prompt()
            .user("Summarise the following text.\n\n$text")
            .call()
            .entity(Summary)
}
```

Let the model call a service by annotating its methods with Spring AI's `@Tool` and passing the service to the client. Describe every tool and parameter; the descriptions are what the model reasons with:

```groovy
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam

class BookService {

    @Tool(description = 'Finds the books of an author')
    List<String> findBooks(@ToolParam(description = 'The name of the author') String author) {
        Book.findAllByAuthor(author)*.title
    }
}

chatClient.prompt()
        .user('Which books did Graeme Rocher write?')
        .tools(bookService)
        .call()
        .content()
```

A memory advisor needs `ChatMemory.CONVERSATION_ID` on every call. Derive it from the current user and session, never a constant, or conversations bleed into each other. Keep `spring.ai.tools.resolution.fallback.enabled` at its default of `false`; enabling it lets a prompt reach every tool bean in the context.

## Streaming an Answer from a Controller

Return the `Flux` from `stream()` directly from the action. Grails commits the response as `text/event-stream`, writes every item as a server-sent event while the container thread is released, and ends with an event named `complete`:

```groovy
package my.app

import org.springframework.ai.chat.client.ChatClient

class ChatController {

    ChatClient.Builder chatClientBuilder

    def ask(String question) {
        chatClientBuilder.build()
                .prompt()
                .user(question)
                .stream()
                .content()
    }
}
```

- Return the `Flux`; do not `render` it, collect it, or `block()` on it.
- `stream().chatResponse()` works too: the plugin renders each `ChatResponse` as its text. Any other item is sent as text or JSON, and a `org.springframework.http.codec.ServerSentEvent` is written with its id, event name and retry.
- Every stream ends with the `complete` event, whose data is empty, `timeout` or `error`; the response is already committed when a model fails, so no error status is possible and nothing of the failure reaches the client. There is no event named `error`, which `EventSource` uses for its own connection failures.
- A browser client closes on `complete`, or `EventSource` reconnects and runs the action again, paying for another model call each time:

```javascript
const source = new EventSource('/chat/ask?question=' + encodeURIComponent(question));
source.onmessage = event => { answer.textContent += event.data; };
source.addEventListener('complete', () => source.close());
```

- A stream has no timeout unless `grails.async.streaming.timeout` sets one; set it, for example to `5m`, to bound how long an answer may take.
- A `Mono` returned from an action is resolved, not streamed: its value is the action's result as a `Promise`'s is. Return the `Flux` to stream.

## Configuration the Plugin Ships

The plugin's defaults sit below `application.yml`, so anything the application sets wins:

| Setting | Plugin default | Spring AI default | Why |
|---------|----------------|-------------------|-----|
| `spring.ai.retry.max-attempts` | `3` | `10` with backoff up to three minutes | A web request cannot wait that long |
| `spring.ai.chat.client.observations.log-prompt`, `log-completion`, `spring.ai.chat.observations.include-error-logging` | `true` in development only | `false` | See what is sent and what comes back while developing; never in test or production |

The plugin selects no provider and no credential. Streaming is configured under `grails.async.streaming` and the test stubs under `grails.ai.stub`.

## Testing

Never call a provider from a test. `grails-testing-support-ai` gives tests a scripted chat model and a deterministic embedding model.

### Unit Tests

Implement `grails.testing.ai.AiUnitTest`, usually together with `ServiceUnitTest`. The context holds a `chatModel`, an `embeddingModel`, a prototype `chatClientBuilder` over the scripted model and, when `spring-ai-vector-store` is on the classpath, an in-memory `vectorStore`; a service gets them by injection as it would get the real ones:

```groovy
import grails.testing.ai.AiUnitTest
import grails.testing.services.ServiceUnitTest
import spock.lang.Specification

class AnswerServiceSpec extends Specification implements ServiceUnitTest<AnswerService>, AiUnitTest {

    void 'answers with the model'() {
        given:
        respondWith('42')

        expect:
        service.answer('What is six times seven?') == '42'
        prompts.last().userMessage.text == 'What is six times seven?'
        prompts.last().systemMessage.text.contains('briefly')
    }
}
```

- `respondWith('a', 'b')` scripts answers in order; `respondWith { Prompt prompt -> ... }` computes one from the prompt; with nothing scripted the model echoes the user's message, or fails when `chatModel.strict = true` (reset with the script before every feature).
- The stubs are registered before `setupSpec`, each only when the spec defines no bean of its type, so a spec's own `ChatModel`, `EmbeddingModel`, `ChatClient.Builder` or `VectorStore` wins; `grails.ai.stub.embeddingDimensions` set in `doWithConfig` sizes the stub embeddings.
- A streamed call answers one word at a time, so code that assembles a stream is exercised as it would be against a real model.
- `prompts` holds every prompt of the current feature method. The script, the prompts and the vector store are cleared before every feature method.
- `vectorStore.add([new Document('...')])` followed by `similaritySearch` finds documents by the words they share with the query; that is enough to test retrieval code.

### Integration and Functional Tests

While `grails-testing-support-ai` is on the test classpath and the application runs in the test environment (elsewhere the stubs stay off and a warning is logged), every `spring.ai.model.*` selection property and `spring.ai.vectorstore.type` default to `none`, so no provider auto-configures a chat, embedding, image, audio or moderation model, and the stubs become the primary chat and embedding models and the vector store; the test environment needs neither an API key nor a database. A provider or store the application names in its configuration is kept, and the stubs are fallback beans, so a model or store the application defines in `resources.groovy` or `doWithSpring` is the one injected. Inject `org.apache.grails.testing.ai.ScriptedChatModel` into an `@Integration` spec, call `reset()` in `setup()`, and script it the same way; the in-memory `ResettableSimpleVectorStore` has `clear()`. To run against the real provider, set `grails.ai.stub.enabled: false` for the test environment.

## Pitfalls

- Spring AI's `Document` has both `getText()` and `isText()`. Under `@CompileStatic` or `@GrailsCompileStatic`, `document.text` resolves to `isText()` and yields a boolean; call `document.getText()`.
- `ChatClient.Builder` is a prototype bean. Inject the builder, not a shared `ChatClient`, unless the application defines one.
- Do not wrap the fluent API in another abstraction; closures already coerce to its consumers and the Spring AI reference applies as written.
- An action that returns a multi-valued `Publisher` streams; it no longer renders its view. A single-valued one, such as a `Mono`, is resolved like a `Promise`. `grails.async.streaming.enabled: false` restores the old behaviour for an application that relied on it.
- Bundled provider starters, hard-coded keys, or a key or key placeholder in `application.yml` are review failures.
- The Spring AI line moves with the Grails version; do not pin a Spring AI version in the application.

## Verification Checklist

Before finishing a change that touches AI code:

1. `./gradlew test` passes without network access, with the scripted model answering.
2. A streaming action is covered by a functional test that reads the `text/event-stream` body and sees the `complete` event.
3. No credential is committed; every key is a `${...}` placeholder.
4. Every new setting is documented and resolves in the application properties reference or the Spring AI reference.
