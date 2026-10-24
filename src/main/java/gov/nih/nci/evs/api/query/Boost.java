package gov.nih.nci.evs.api.query;

import gov.nih.nci.evs.api.model.Terminology;
import java.util.List;
import java.util.Objects;
import org.opensearch.data.core.OpenSearchOperations;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;

/**
 * Matches what the child query matches, but scores every match the same fixed amount, ignoring how
 * well it matched. Inside an {@link Or}, the scores of the alternatives that matched add up, so
 * boosted alternatives sort matches into tiers.
 */
public class Boost extends EvsQuery {

  /** The query whose matches are scored. */
  private final EvsQuery child;

  /** The score every match gets. */
  private final float score;

  /**
   * Instantiates a {@link Boost}.
   *
   * @param terminologies the terminologies being searched
   * @param child the query whose matches are scored
   * @param score the score every match gets
   */
  public Boost(final List<Terminology> terminologies, final EvsQuery child, final float score) {
    super(terminologies);
    this.child = Objects.requireNonNull(child, "child");
    this.score = score;
  }

  /* see superclass */
  @Override
  public QueryBuilder fetchOpenSearchQuery(final OpenSearchOperations operations) {
    return QueryBuilders.constantScoreQuery(child.fetchOpenSearchQuery(operations)).boost(score);
  }
}
