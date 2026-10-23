package gov.nih.nci.evs.api.query;

import gov.nih.nci.evs.api.model.Terminology;
import java.util.List;

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
   * @param terminologies the terminologies being searched
   * @param descendantQuery the query matching the anchor concepts
   */
  public AncestorOrSelfOf(final List<Terminology> terminologies, final EvsQuery descendantQuery) {
    super(terminologies, descendantQuery, ANY_LEVEL, WITH_SELF);
  }

  /**
   * Instantiates an {@link AncestorOrSelfOf} for the concepts an inner query matches and those
   * within {@code maxLevel} levels above them.
   *
   * @param terminologies the terminologies being searched
   * @param descendantQuery the query matching the anchor concepts
   * @param maxLevel the maximum distance above the anchors, at least 1
   */
  public AncestorOrSelfOf(
      final List<Terminology> terminologies, final EvsQuery descendantQuery, final int maxLevel) {
    super(terminologies, descendantQuery, maxLevel, WITH_SELF);
  }

  /**
   * Instantiates an {@link AncestorOrSelfOf} for one concept and everything above it.
   *
   * @param terminologies the terminologies being searched
   * @param code the anchor concept's code
   */
  public AncestorOrSelfOf(final List<Terminology> terminologies, final String code) {
    super(terminologies, getCodeQuery(terminologies, code), ANY_LEVEL, WITH_SELF);
  }

  /**
   * Instantiates an {@link AncestorOrSelfOf} for one concept and those within {@code maxLevel}
   * levels above it.
   *
   * @param terminologies the terminologies being searched
   * @param code the anchor concept's code
   * @param maxLevel the maximum distance above the anchor, at least 1
   */
  public AncestorOrSelfOf(
      final List<Terminology> terminologies, final String code, final int maxLevel) {
    super(terminologies, getCodeQuery(terminologies, code), maxLevel, WITH_SELF);
  }
}
