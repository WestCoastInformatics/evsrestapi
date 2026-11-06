package gov.nih.nci.evs.api.query;

import static gov.nih.nci.evs.api.query.RawBoolQueries.getAnyOfQuery;

import gov.nih.nci.evs.api.model.Terminology;
import java.util.List;
import org.opensearch.data.core.OpenSearchOperations;
import org.opensearch.index.query.QueryBuilder;

/**
 * Matches the target concepts and every concept below them, in the same terminology version. For
 * example, Skin and everything below Skin. Like {@link HasAncestor}, a target is a concept whose
 * name or code matches the {@link ValueMatch}, and {@code maxLevel} limits how far below the
 * targets to go (the targets themselves are depth 0 and always match).
 */
public class HasAncestorOrIs extends HasAncestor {

  /** Instantiates an empty {@link HasAncestorOrIs}. */
  public HasAncestorOrIs() {
    // n/a
  }

  /**
   * Instantiates a {@link HasAncestorOrIs} for the target concepts and everything below them.
   *
   * @param target how the target concepts' name or code must match
   */
  public HasAncestorOrIs(final ValueMatch target) {
    super(target);
  }

  /**
   * Instantiates a {@link HasAncestorOrIs} for the target concepts and those within {@code
   * maxLevel} levels below them.
   *
   * @param target how the target concepts' name or code must match
   * @param maxLevel the maximum depth below the targets, at least 1
   */
  public HasAncestorOrIs(final ValueMatch target, final int maxLevel) {
    super(target, maxLevel);
  }

  /**
   * Instantiates a {@link HasAncestorOrIs} for one concept and everything below it.
   *
   * @param code the target concept's code
   */
  public HasAncestorOrIs(final String code) {
    super(code);
  }

  /**
   * Instantiates a {@link HasAncestorOrIs} for one concept and those within {@code maxLevel} levels
   * below it.
   *
   * @param code the target concept's code
   * @param maxLevel the maximum depth below the target, at least 1
   */
  public HasAncestorOrIs(final String code, final int maxLevel) {
    super(code, maxLevel);
  }

  /**
   * Returns the query matching the targets' descendants, or the targets themselves.
   *
   * @param operations the OpenSearch operations
   * @param terminologies the terminologies being searched
   * @return the query builder
   */
  @Override
  public QueryBuilder fetchOpenSearchQuery(
      final OpenSearchOperations operations, final List<Terminology> terminologies) {
    return getAnyOfQuery(
        List.of(
            super.fetchOpenSearchQuery(operations, terminologies),
            getTargetsQuery(operations, terminologies)));
  }
}
