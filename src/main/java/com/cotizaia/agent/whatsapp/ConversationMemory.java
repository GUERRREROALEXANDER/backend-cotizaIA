package com.cotizaia.agent.whatsapp;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What each WhatsApp sender and the assistant said recently, so the assistant does not greet twice or ask again for
 * what it was already told. Kept only in memory and only for a while: a chat is forgotten after
 * {@link #IDLE} without messages, after a restart, or when the oldest of {@link #MAX_SENDERS} chats is pushed out.
 */
public class ConversationMemory {

    public static final Duration IDLE = Duration.ofMinutes(30);
    /** Lines kept per chat: the last ten exchanges. */
    static final int MAX_LINES = 20;
    static final int MAX_SENDERS = 2_000;

    public enum Speaker { CLIENTE, ASISTENTE }

    public record Line(Speaker speaker, String text) {
    }

    private static final class Chat {
        private final Deque<Line> lines = new ArrayDeque<>();
        private Instant lastAt;
    }

    private final Clock clock;
    private final Map<String, Chat> chats = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Chat> eldest) {
            return size() > MAX_SENDERS;
        }
    };

    public ConversationMemory(Clock clock) {
        this.clock = clock;
    }

    /** The recent lines of this chat, oldest first; empty for a new chat or one idle for too long. */
    public synchronized List<Line> history(String sender) {
        Chat chat = chats.get(sender);
        if (chat == null || !chat.lastAt.plus(IDLE).isAfter(clock.instant())) {
            chats.remove(sender);
            return List.of();
        }
        return List.copyOf(chat.lines);
    }

    public synchronized void add(String sender, Speaker speaker, String text) {
        Chat chat = chats.computeIfAbsent(sender, ignored -> new Chat());
        chat.lines.addLast(new Line(speaker, text));
        while (chat.lines.size() > MAX_LINES) {
            chat.lines.removeFirst();
        }
        chat.lastAt = clock.instant();
    }
}
