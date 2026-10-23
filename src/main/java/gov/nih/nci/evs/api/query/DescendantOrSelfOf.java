package gov.nih.nci.evs.api.query;

import gov.nih.nci.evs.api.model.Terminology;
import java.util.List;

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
   * @param terminologies the terminologies being searched
   * @param ancestorQuery the query matching the anchor concepts
   */
  public DescendantOrSelfOf(final List<Terminology> terminologies, final EvsQuery ancestorQuery) {
    super(terminologies, ancestorQuery, null, true);
  }

  /**
   * Instantiates a {@link DescendantOrSelfOf} for the concepts an inner query matches and those
   * within {@code maxLevel} levels below them.
   *
   * @param terminologies the terminologies being searched
   * @param ancestorQuery the query matching the anchor concepts
   * @param maxLevel the maximum depth below the anchors, at least 1
   */
  public DescendantOrSelfOf(
      final List<Terminology> terminologies, final EvsQuery ancestorQuery, final int maxLevel) {
    super(terminologies, ancestorQuery, maxLevel, true);
  }

  /**
   * Instantiates a {@link DescendantOrSelfOf} for one concept and everything below it.
   *
   * @param terminologies the terminologies being searched
   * @param code the anchor concept's code
   */
  public DescendantOrSelfOf(final List<Terminology> terminologies, final String code) {
    super(terminologies, codeQuery(terminologies, code), null, true);
  }

  /**
   * Instantiates a {@link DescendantOrSelfOf} for one concept and those within {@code maxLevel}
   * levels below it.
   *
   * @param terminologies the terminologies being searched
   * @param code the anchor concept's code
   * @param maxLevel the maximum depth below the anchor, at least 1
   */
  public DescendantOrSelfOf(
      final List<Terminology> terminologies, final String code, final int maxLevel) {
    super(terminologies, codeQuery(terminologies, code), maxLevel, true);
  }
}
