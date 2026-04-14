package io.darqlab.papyrus.core.service;

import io.darqlab.papyrus.core.domain.ChatTurn;

import java.util.List;
import java.util.stream.Stream;

public interface ChatService {

    /**
     * Stream chat response tokens given a conversation history and a system prompt.
     *
     * @param history      the full conversation turns (user + assistant alternating)
     * @param systemPrompt the system prompt to prepend
     * @return a stream of text delta tokens to be sent to the client
     */
    Stream<String> streamChat(List<ChatTurn> history, String systemPrompt);
}
