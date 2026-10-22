package gov.nih.nci.evs.api.query;

import gov.nih.nci.evs.api.model.ConceptMinimal;
import gov.nih.nci.evs.api.model.Terminology;
import gov.nih.nci.evs.api.util.ConceptUtils;
import java.util.List;
import java.util.Map;
import org.opensearch.data.core.OpenSearchOperations;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.script.Script;
import org.opensearch.script.ScriptType;
import org.opensearch.search.sort.ScriptSortBuilder;
import org.opensearch.search.sort.SortBuilder;
import org.opensearch.search.sort.SortBuilders;
import org.opensearch.search.sort.SortOrder;

/**
 * Completes a partially typed term against concept preferred names.
 *
 * <ul>
 *   <li>Matching is {@link SearchAsYouType} on the preferred name: every typed word must appear, in
 *       any order, with only the last word allowed to be partial. Case and punctuation are ignored.
 *   <li>Ranking puts general concepts (those with more descendants) with short names first, with a
 *       small preference for names that start with the typed text. See {@link #getSorts()}.
 *   <li>Run it with {@link #getNativeSearchQuery}, which applies the ranking.
 * </ul>
 */
public class Typeahead extends EvsQuery {

  /** The field typeahead matches against: the search_as_you_type subfield of preferred names. */
  private static final String TYPEAHEAD_FIELD = "name." + ConceptMinimal.SEARCH_AS_YOU_TYPE_SUFFIX;

  /** Keyword field holding the normalized preferred name, used to rank typeahead matches. */
  private static final String NORMALIZED_NAME_FIELD = "normName";

  /**
   * Bonus for names that start with the typed text. Small enough that a much more general concept
   * whose name only contains the typed words, like "Acute Lymphoblastic Leukemia" for
   * "lymphoblastic", can still rank above it.
   */
  private static final double STARTS_WITH_BONUS = 1.0;

  /**
   * How many descendants one extra word in a name is worth when ranking typeahead matches. With a
   * penalty of 1, a name with one more word needs about e (2.7) times as many descendants to rank
   * the same.
   */
  private static final double WORD_PENALTY = 1.0;

  /** Field holding each concept's number of descendants, stored when concepts are loaded. */
  private static final String DESCENDANT_COUNT_FIELD = "descendantCount";

  /**
   * Field counted in place of {@link #DESCENDANT_COUNT_FIELD} on indexes loaded before it existed.
   * Every descendant has a leaf flag, and boolean doc values keep duplicates, so the number of leaf
   * values is the number of descendants.
   */
  private static final String DESCENDANT_LEAF_FIELD = "descendants.leaf";

  /**
   * Ranks general concepts with short names first: log(1 + descendant count), minus the word
   * penalty for each word in the normalized name, plus the starts-with bonus when the normalized
   * name starts with the typed text. The log keeps concepts with huge hierarchies from outranking
   * everything. A concept without a normalized name sorts last, and one without a descendant count
   * counts as having none.
   *
   * <p>TODO: Drop the {@link #DESCENDANT_LEAF_FIELD} fallback once every index has been reloaded
   * with {@link #DESCENDANT_COUNT_FIELD}.
   */
  private static final String GENERAL_SHORT_NAME_FIRST_SOURCE =
      """
      boolean hasNormalizedName = doc['%1$s'].size() != 0;
      if (!hasNormalizedName) {
        return -Double.MAX_VALUE;
      }
      String name = doc['%1$s'].value;

      int wordCount = 1;
      for (int i = 0; i < name.length(); i++) {
        boolean isWordBreak = name.charAt(i) == (char) ' ';
        if (isWordBreak) {
          wordCount++;
        }
      }

      boolean isNameStartingWithIncompleteTerm = name.startsWith(params.normalizedIncompleteTerm);
      double startsWithBonus = isNameStartingWithIncompleteTerm ? params.startsWithBonus : 0;

      boolean hasDescendantCountField = doc.containsKey('%2$s');
      boolean hasStoredDescendantCount = hasDescendantCountField && doc['%2$s'].size() != 0;
      int descendantCount = 0;
      if (hasStoredDescendantCount) {
        descendantCount = (int) doc['%2$s'].value;
      } else if (!hasDescendantCountField) {
        descendantCount = doc['%3$s'].size();
      }

      return Math.log(1 + descendantCount) - params.wordPenalty * wordCount + startsWithBonus;
      """
          .formatted(NORMALIZED_NAME_FIELD, DESCENDANT_COUNT_FIELD, DESCENDANT_LEAF_FIELD);

  /** The text typed so far, as entered. The search_as_you_type analyzer handles normalization. */
  private final String incompleteTerm;

  /**
   * Instantiates a {@link Typeahead}.
   *
   * @param terminologies the terminologies being searched
   * @param incompleteTerm the text typed so far; must contain at least one word (see {@link
   *     #getHasWordsToComplete(String)})
   */
  public Typeahead(final List<Terminology> terminologies, final String incompleteTerm) {
    super(terminologies);
    final boolean hasWordsToComplete = getHasWordsToComplete(incompleteTerm);
    if (!hasWordsToComplete) {
      throw new IllegalArgumentException("Typeahead term has no words: " + incompleteTerm);
    }
    this.incompleteTerm = incompleteTerm;
  }

  /**
   * Indicates whether the typed text has anything to complete. Null, blank, and punctuation-only
   * text has no words.
   *
   * @param incompleteTerm the text typed so far
   * @return whether the text has at least one word
   */
  public static boolean getHasWordsToComplete(final String incompleteTerm) {
    final boolean hasText = incompleteTerm != null;
    return hasText && !ConceptUtils.normalize(incompleteTerm).isEmpty();
  }

  /**
   * Returns the text typed so far.
   *
   * @return the incomplete term
   */
  public String getIncompleteTerm() {
    return incompleteTerm;
  }

  /* see superclass */
  @Override
  public QueryBuilder fetchOpenSearchQuery(final OpenSearchOperations operations) {
    return new SearchAsYouType(terminologies, TYPEAHEAD_FIELD, incompleteTerm)
        .fetchOpenSearchQuery(operations);
  }

  /**
   * Orders matches best first: general concepts with short names first (see {@link
   * #GENERAL_SHORT_NAME_FIRST_SOURCE}). Ties fall through to the default tie-break in {@link
   * EvsQuery#getNativeSearchQuery}.
   *
   * @return the ranking sort
   */
  @Override
  public List<SortBuilder<?>> getSorts() {
    return List.of(
        SortBuilders.scriptSort(getRankingScript(), ScriptSortBuilder.ScriptSortType.NUMBER)
            .order(SortOrder.DESC));
  }

  /**
   * Builds the script that ranks typeahead matches. See {@link #GENERAL_SHORT_NAME_FIRST_SOURCE}.
   *
   * @return the ranking script
   */
  private Script getRankingScript() {
    // Normalized like NORMALIZED_NAME_FIELD so the starts-with comparison lines up
    final String normalizedIncompleteTerm = ConceptUtils.normalize(incompleteTerm);
    return new Script(
        ScriptType.INLINE,
        Script.DEFAULT_SCRIPT_LANG,
        GENERAL_SHORT_NAME_FIRST_SOURCE,
        Map.of(
            "normalizedIncompleteTerm", normalizedIncompleteTerm,
            "wordPenalty", WORD_PENALTY,
            "startsWithBonus", STARTS_WITH_BONUS));
  }
}
