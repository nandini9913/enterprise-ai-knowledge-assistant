package com.nandini.knowledgeassistant.rag;

public enum AnswerStatus {
    /** The answer is grounded in and cites at least one retrieved source. */
    ANSWERED,
    /** Nothing relevant was retrieved, or the model could not answer from the sources. */
    INSUFFICIENT_CONTEXT
}
