package com.cotizaia.agent;

/**
 * One link of the agent's Chain of Responsibility (project.txt section 6
 * pattern 10). The recorder runs the links in order and logs each one; a link
 * never touches the log itself, so the observability concern stays separate
 * from the pipeline logic.
 *
 * <p>{@link #name()} is the handler name written to {@code agent_steps.handler};
 * {@link #execute} returns a short output summary for the live feed and may
 * throw to signal that the chain halts.
 */
public interface PipelineStep {

    String name();

    /**
     * @param context the value passed from the previous link
     * @return the value handed to the next link
     * @throws RuntimeException to halt the chain; the recorder marks the run FAILED
     */
    Object execute(Object context);
}
