package gov.nih.nci.evs.api.query;

import java.util.List;

import org.opensearch.index.query.BoolQueryBuilder;

import gov.nih.nci.evs.api.model.Terminology;

public abstract class EvsQuery {
    public List<Terminology> terminologies; // do not mutate

    public EvsQuery(List<Terminology> terminologies) {
        this.terminologies = terminologies;
    }

    abstract public BoolQueryBuilder getQueryBuilder();
}
