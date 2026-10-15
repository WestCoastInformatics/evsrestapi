package gov.nih.nci.evs.api.query;

import org.opensearch.index.query.QueryBuilder;

/**
 * Runs the wrapped query ignoring case, so "MELANOMA" matches both "Melanoma" and "melanoma". The
 * wrapped query is not changed and remains case sensitive on its own.
 *
 * <p>Only queries that implement {@link SupportsIgnoringCase} can be wrapped. {@link Fuzzy} cannot,
 * because OpenSearch has no case-insensitive fuzzy query.
 */
public class CaseInsensitive extends EvsQuery {

  /** The wrapped query. */
  private final SupportsIgnoringCase query;

  /**
   * Instantiates a {@link CaseInsensitive}.
   *
   * @param <Q> the wrapped query type
   * @param query the query to run ignoring case
   */
  public <Q extends EvsQuery & SupportsIgnoringCase> CaseInsensitive(final Q query) {
    super(query.getTerminologies());
    this.query = query;
  }

  /* see superclass */
  @Override
  public QueryBuilder fetchOpenSearchQuery() {
    return query.fetchCaseInsensitiveOpenSearchQuery();
  }
}
