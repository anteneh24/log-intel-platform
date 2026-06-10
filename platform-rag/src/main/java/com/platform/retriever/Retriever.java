package com.platform.retriever;

import com.platform.model.RetrievalQuery;
import com.platform.model.RetrievedChunk;

import java.util.List;

public interface Retriever {

    List<RetrievedChunk> retriever(RetrievalQuery query);
}
