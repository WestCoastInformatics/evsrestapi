package gov.nih.nci.evs.api.query;

import java.util.List;

import org.opensearch.index.query.BoolQueryBuilder;

import gov.nih.nci.evs.api.model.SearchCriteria;
import gov.nih.nci.evs.api.model.Terminology;

public class Contains extends EvsQuery {
    private String field;

    public Contains(List<Terminology> terminologies, String field) {
        super(terminologies);
        this.field = field;
    }

    @Override
    public BoolQueryBuilder getQueryBuilder() {

    }
}
