package com.nandini.knowledgeassistant.ingestion.queue;

import com.nandini.knowledgeassistant.document.DocumentEvents;

/** Hands a processing request to the pipeline - in-process or via SQS. */
public interface ProcessingDispatcher {

    void dispatch(DocumentEvents.ProcessingRequested request);
}
