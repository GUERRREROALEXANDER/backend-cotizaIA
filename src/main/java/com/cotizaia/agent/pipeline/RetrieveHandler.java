package com.cotizaia.agent.pipeline;

import com.cotizaia.agent.knowledge.PastItemRetriever;
import com.cotizaia.agent.knowledge.RetrievedChunk;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Loads the agency's most similar past items for every extracted requirement.
 *
 * <p>Memory is an enhancement, never a dependency: a broken embedding provider degrades the run to catalog
 * estimation with a review flag instead of failing the quote. The handler never throws for memory errors.
 *
 * <p>Design pattern - <b>Chain of Responsibility</b>: this ConcreteHandler enriches drafts with evidence before the
 * estimation successor runs.
 */
@Component
public class RetrieveHandler extends PipelineHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RetrieveHandler.class);

    private final PastItemRetriever retriever;

    private final EvidenceQueryBuilder queries;

    private final RetrievalProperties properties;

    public RetrieveHandler(PastItemRetriever retriever, EvidenceQueryBuilder queries, RetrievalProperties properties) {
        this.retriever = retriever;
        this.queries = queries;
        this.properties = properties;
    }

    @Override
    public String name() {
        return "Retrieve";
    }

    @Override
    protected HandlerResult process(PipelineContext context) {
        if (!properties.isEnabled()) {
            return HandlerResult.proceed("memory disabled");
        }
        try {
            int withEvidence = 0;
            for (RequirementDraft draft : context.getRequirements()) {
                String query = queries.query(context.evidenceText(), draft.getRequirementType());
                List<RetrievedChunk> hits = retriever.retrieve(context.getAgencyId(), query,
                        draft.getRequirementType().getName(), properties.getK());
                draft.setEvidence(hits == null ? List.of() : hits);
                if (!draft.getEvidence().isEmpty()) {
                    withEvidence++;
                }
            }
            return HandlerResult.proceed(withEvidence + "/" + context.getRequirements().size()
                    + " requirements have past evidence");
        } catch (RuntimeException exception) {
            LOGGER.warn("Memory retrieval unavailable; estimating from the catalog", exception);
            for (RequirementDraft draft : context.getRequirements()) {
                draft.setEvidence(List.of());
            }
            return HandlerResult.flag("memory unavailable");
        }
    }
}
