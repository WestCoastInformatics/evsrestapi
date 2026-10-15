package gov.nih.nci.evs.api.query;

import gov.nih.nci.evs.api.model.Terminology;
import java.util.List;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;

/** Matches values that begin with the term (case sensitive). */
public class StartsWith extends AtomicQuery {

  /**
   * Instantiates a {@link StartsWith}.
   *
   * @param terminologies the terminologies being searched
   * @param field the field to match against
   * @param term the prefix the value must begin with
   */
  public StartsWith(final List<Terminology> terminologies, final String field, final String term) {
    super(terminologies, field, term);
  }

  /* see superclass */
  @Override
  public QueryBuilder getQueryBuilder() {
    return QueryBuilders.prefixQuery(field, term);
  }
}
