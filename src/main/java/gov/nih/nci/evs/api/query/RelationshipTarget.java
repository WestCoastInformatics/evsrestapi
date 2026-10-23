package gov.nih.nci.evs.api.query;

import java.util.Objects;

/**
 * The concepts a relationship must point to, for {@link HasRelationship}. Given as a query over
 * concepts, e.g. {@code FieldMatch("code", Exact("C12470"))} for Skin.
 *
 * <p>A relationship entry only stores its target's code, so the target query describes a different
 * concept from the one being matched and can't run inside the relationship query. {@link
 * HasRelationship} runs it first (see {@link EvsQuery#declareDependencies}) and matches the
 * concepts it found. A target query can hold relationship targets of its own, which run first in
 * the same way.
 */
public final class RelationshipTarget {

  /** The query selecting the target concepts. */
  private final EvsQuery query;

  /**
   * Instantiates a {@link RelationshipTarget}.
   *
   * @param query the query selecting the target concepts
   */
  public RelationshipTarget(final EvsQuery query) {
    this.query = Objects.requireNonNull(query, "query");
  }

  /**
   * Returns the query selecting the target concepts.
   *
   * @return the query
   */
  public EvsQuery getQuery() {
    return query;
  }
}
