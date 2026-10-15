package gov.nih.nci.evs.api.query;

import gov.nih.nci.evs.api.model.Terminology;
import java.util.List;
import org.opensearch.index.query.BoolQueryBuilder;

public abstract class EvsQuery {
  public List<Terminology> terminologies; // do not mutate

  public EvsQuery(List<Terminology> terminologies) {
    this.terminologies = terminologies;
  }

  public abstract BoolQueryBuilder fetchOpenSearchQuery();
}
