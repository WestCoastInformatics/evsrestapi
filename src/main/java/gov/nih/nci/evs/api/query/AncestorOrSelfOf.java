package gov.nih.nci.evs.api.query;

/**
 * Matches the concepts an inner query matches and every concept above them, in the same terminology
 * version: the upward counterpart of {@link DescendantOrSelfOf}. For example, Breast Carcinoma and
 * everything above it. {@code maxLevel} limits how far above the anchors to go (the anchors
 * themselves are distance 0 and always match).
 */
public class AncestorOrSelfOf extends AncestorOf {

  /**
   * Instantiates an {@link AncestorOrSelfOf} for the concepts an inner query matches and everything
   * above them.
   *
   * @param descendantQuery the query matching the anchor concepts
   */
  public AncestorOrSelfOf(final EvsQuery descendantQuery) {
    super(descendantQuery, ANY_LEVEL, WITH_SELF);
  }

  /**
   * Instantiates an {@link AncestorOrSelfOf} for the concepts an inner query matches and those
   * within {@code maxLevel} levels above them.
   *
   * @param descendantQuery the query matching the anchor concepts
   * @param maxLevel the maximum distance above the anchors, at least 1
   */
  public AncestorOrSelfOf(final EvsQuery descendantQuery, final int maxLevel) {
    super(descendantQuery, maxLevel, WITH_SELF);
  }

  /**
   * Instantiates an {@link AncestorOrSelfOf} for one concept and everything above it.
   *
   * @param code the anchor concept's code
   */
  public AncestorOrSelfOf(final String code) {
    super(getCodeQuery(code), ANY_LEVEL, WITH_SELF);
  }

  /**
   * Instantiates an {@link AncestorOrSelfOf} for one concept and those within {@code maxLevel}
   * levels above it.
   *
   * @param code the anchor concept's code
   * @param maxLevel the maximum distance above the anchor, at least 1
   */
  public AncestorOrSelfOf(final String code, final int maxLevel) {
    super(getCodeQuery(code), maxLevel, WITH_SELF);
  }
}
