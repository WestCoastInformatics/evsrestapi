package gov.nih.nci.evs.api.query;

import gov.nih.nci.evs.api.model.Terminology;
import java.util.List;
import java.util.Objects;
import org.opensearch.data.core.OpenSearchOperations;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;

/**
 * Matches concepts that the child query doesn't match, including concepts missing the child's
 * field. E.g. {@code new Not(t, new InSubset(t, send))} keeps concepts outside SEND, including
 * concepts in no subset at all.
 *
 * <p>Adds nothing to the score, so wrapping it in an {@link And} doesn't change how the other
 * children rank.
 */
public class Not extends EvsQuery {

  /** The query concepts must not match. */
  private final EvsQuery child;

  /**
   * Instantiates a {@link Not}.
   *
   * @param terminologies the terminologies being searched
   * @param child the query concepts must not match
   */
  public Not(final List<Terminology> terminologies, final EvsQuery child) {
    super(terminologies);
    this.child = Objects.requireNonNull(child, "child");
  }

  /* see superclass */
  @Override
  public QueryBuilder fetchOpenSearchQuery(final OpenSearchOperations operations) {
    // A bool query with only must_not would score every match 1; a match_all filter scores 0
    return QueryBuilders.boolQuery()
        .filter(QueryBuilders.matchAllQuery())
        .mustNot(child.fetchOpenSearchQuery(operations));
  }
}
