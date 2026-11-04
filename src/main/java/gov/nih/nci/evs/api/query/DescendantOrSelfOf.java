package gov.nih.nci.evs.api.query;

/**
 * Matches the concepts an inner query matches and every concept below them, in the same terminology
 * version. For example, Skin and everything below Skin. Like {@link DescendantOf}, the inner query
 * can be any {@link EvsQuery}, and {@code maxLevel} limits how far below the anchors to go (the
 * anchors themselves are depth 0 and always match).
 */
public class DescendantOrSelfOf extends DescendantOf {

  /**
   * Instantiates a {@link DescendantOrSelfOf} for the concepts an inner query matches and
   * everything below them.
   *
   * @param ancestorQuery the query matching the anchor concepts
   */
  public DescendantOrSelfOf(final EvsQuery ancestorQuery) {
    super(ancestorQuery, ANY_LEVEL, WITH_SELF);
  }

  /**
   * Instantiates a {@link DescendantOrSelfOf} for the concepts an inner query matches and those
   * within {@code maxLevel} levels below them.
   *
   * @param ancestorQuery the query matching the anchor concepts
   * @param maxLevel the maximum depth below the anchors, at least 1
   */
  public DescendantOrSelfOf(final EvsQuery ancestorQuery, final int maxLevel) {
    super(ancestorQuery, maxLevel, WITH_SELF);
  }

  /**
   * Instantiates a {@link DescendantOrSelfOf} for one concept and everything below it.
   *
   * @param code the anchor concept's code
   */
  public DescendantOrSelfOf(final String code) {
    super(getCodeQuery(code), ANY_LEVEL, WITH_SELF);
  }

  /**
   * Instantiates a {@link DescendantOrSelfOf} for one concept and those within {@code maxLevel}
   * levels below it.
   *
   * @param code the anchor concept's code
   * @param maxLevel the maximum depth below the anchor, at least 1
   */
  public DescendantOrSelfOf(final String code, final int maxLevel) {
    super(getCodeQuery(code), maxLevel, WITH_SELF);
  }
}
