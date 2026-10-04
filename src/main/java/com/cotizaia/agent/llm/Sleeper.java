package com.cotizaia.agent.llm;

import java.time.Duration;

/** Allows retry delays to be observed without waiting in tests. */
@FunctionalInterface
public interface Sleeper {

    void sleep(Duration duration) throws InterruptedException;
}
