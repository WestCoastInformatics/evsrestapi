package gov.nih.nci.evs.api.query;

import gov.nih.nci.evs.api.model.Terminology;
import java.util.Set;
import org.opensearch.data.core.OpenSearchOperations;
import org.opensearch.index.query.QueryBuilder;

/**
 * Matches concepts strictly above any concept an inner query matches, in the same terminology
 * version: the upward counterpart of {@link DescendantOf}. For example, everything above Breast
 * Carcinoma, or the parents of any concept named "... Melanoma". The inner query can be any {@link
 * EvsQuery}, including another hierarchy node, so these nest with each other and with {@link
 * DescendantOf}: the ancestors of X's children is {@code new AncestorOf(new DescendantOf(x, 1))}.
 *
 * <p>An optional {@code maxLevel} limits how far above: 1 means direct parents. Distance is the
 * shortest, so a concept that is a direct parent of X on one path and further up on another counts
 * as 1.
 *
 * <p>The ancestors come from the anchors' stored paths to root, which every index has, so unlike
 * {@link DescendantOf} there is no separate path for older indexes and nothing to remove after
 * reindexing.
 *
 * @see AncestorOrSelfOf
 */
public class AncestorOf extends HierarchyQuery {

  /**
   * Instantiates an {@link AncestorOf} for everything above the concepts an inner query matches.
   *
   * @param descendantQuery the query matching the anchor concepts
   */
  public AncestorOf(final EvsQuery descendantQuery) {
    super(descendantQuery, ANY_LEVEL, WITHOUT_SELF);
  }

  /**
   * Instantiates an {@link AncestorOf} for concepts within {@code maxLevel} levels above the
   * concepts an inner query matches.
   *
   * @param descendantQuery the query matching the anchor concepts
   * @param maxLevel the maximum distance above the anchors, at least 1
   */
  public AncestorOf(final EvsQuery descendantQuery, final int maxLevel) {
    super(descendantQuery, maxLevel, WITHOUT_SELF);
  }

  /**
   * Instantiates an {@link AncestorOf} for everything above one concept.
   *
   * @param code the anchor concept's code
   */
  public AncestorOf(final String code) {
    super(getCodeQuery(code), ANY_LEVEL, WITHOUT_SELF);
  }

  /**
   * Instantiates an {@link AncestorOf} for concepts within {@code maxLevel} levels above one
   * concept.
   *
   * @param code the anchor concept's code
   * @param maxLevel the maximum distance above the anchor, at least 1
   */
  public AncestorOf(final String code, final int maxLevel) {
    super(getCodeQuery(code), maxLevel, WITHOUT_SELF);
  }

  /**
   * Instantiates an {@link AncestorOf}, optionally matching the anchors themselves. For {@link
   * AncestorOrSelfOf}.
   *
   * @param descendantQuery the query matching the anchor concepts
   * @param maxLevel the maximum distance above the anchors, at least 1, or null for any distance
   * @param isSelfIncluded whether the anchors themselves match
   */
  protected AncestorOf(
      final EvsQuery descendantQuery, final Integer maxLevel, final boolean isSelfIncluded) {
    super(descendantQuery, maxLevel, isSelfIncluded);
  }

  /* see superclass */
  @Override
  protected QueryBuilder fetchRelativesQuery(
      final OpenSearchOperations operations,
      final Terminology terminology,
      final Set<String> anchorCodes) {
    final Set<String> ancestorCodes =
        StoredPaths.fetchAncestorCodes(operations, terminology, anchorCodes, getMaxLevel());

    return getCodeInQuery(operations, terminology, ancestorCodes);
  }
}
