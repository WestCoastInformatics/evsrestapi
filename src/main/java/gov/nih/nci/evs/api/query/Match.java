package gov.nih.nci.evs.api.query;

import gov.nih.nci.evs.api.model.Terminology;
import java.util.List;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;

/** Matches values exactly equal to the term (case sensitive). */
public class Match extends AtomicQuery implements SupportsIgnoringCase {

  /**
   * Instantiates a {@link Match}.
   *
   * @param terminologies the terminologies being searched
   * @param field the field to match against
   * @param term the term the value must equal
   */
  public Match(final List<Terminology> terminologies, final String field, final String term) {
    super(terminologies, field, term);
  }

  /* see superclass */
  @Override
  public QueryBuilder getQueryBuilder() {
    return QueryBuilders.termQuery(field, term);
  }

  /* see superclass */
  @Override
  public QueryBuilder getCaseInsensitiveQueryBuilder() {
    return QueryBuilders.termQuery(field, term).caseInsensitive(true);
  }
}
