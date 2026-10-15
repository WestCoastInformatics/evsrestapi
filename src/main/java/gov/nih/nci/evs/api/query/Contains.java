package gov.nih.nci.evs.api.query;

import gov.nih.nci.evs.api.model.Terminology;
import java.util.List;
import org.opensearch.index.query.BoolQueryBuilder;

public class Contains extends EvsQuery {
  private String field;

  public Contains(List<Terminology> terminologies, String field) {
    super(terminologies);
    this.field = field;
  }

  @Override
  public BoolQueryBuilder fetchOpenSearchQuery() {}
}
