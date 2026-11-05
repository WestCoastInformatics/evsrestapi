package gov.nih.nci.evs.api.query;

import java.util.Objects;
import java.util.function.UnaryOperator;
import org.opensearch.index.query.MultiMatchQueryBuilder;
import org.opensearch.index.query.Operator;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;

/**
 * Matches values as the user types, using an OpenSearch {@code search_as_you_type} field.
 *
 * <ul>
 *   <li>Every typed word must appear, in any order: "giant subep" finds "Subependymal giant cell
 *       astrocytoma".
 *   <li>Only the last word may be partial: "chronic granu" finds "Chronic granulomatous disease",
 *       but "chro granu" finds nothing because "chro" is not a whole word.
 *   <li>Case is ignored, because the field's analyzer lowercases both the value and the term.
 * </ul>
 *
 * <p>The field must be mapped as {@code search_as_you_type}. OpenSearch then also indexes the
 * word-pair and word-triple subfields this query searches, which rank names with the words in the
 * typed order higher.
 */
public class SearchAsYouType implements ValueMatch {

  /** Subfield holding adjacent word pairs, created by the search_as_you_type mapping. */
  private static final String WORD_PAIRS_SUFFIX = "._2gram";

  /** Subfield holding adjacent word triples, created by the search_as_you_type mapping. */
  private static final String WORD_TRIPLES_SUFFIX = "._3gram";

  /** The text typed so far. */
  private String term;

  /** Instantiates an empty {@link SearchAsYouType}. */
  public SearchAsYouType() {
    // n/a
  }

  /**
   * Instantiates a {@link SearchAsYouType}.
   *
   * @param term the text typed so far
   */
  public SearchAsYouType(final String term) {
    this.term = Objects.requireNonNull(term, "term");
  }

  /**
   * Returns the text typed so far.
   *
   * @return the term
   */
  public String getTerm() {
    return term;
  }

  /**
   * Sets the text typed so far.
   *
   * @param term the text typed so far
   */
  public void setTerm(final String term) {
    this.term = term;
  }

  /* see superclass */
  @Override
  public SearchAsYouType getWithTermsMapped(final UnaryOperator<String> termMapper) {
    return new SearchAsYouType(termMapper.apply(term));
  }

  /* see superclass */
  @Override
  public QueryBuilder getQueryBuilder(final String field) {
    /**
     * The recommended way to query a search_as_you_type field is using a multi_match query with the
     * type set to bool_prefix. This targets the root field and its generated subfields.
     */
    return QueryBuilders.multiMatchQuery(
            term, field, field + WORD_PAIRS_SUFFIX, field + WORD_TRIPLES_SUFFIX)
        .type(MultiMatchQueryBuilder.Type.BOOL_PREFIX)
        // Require every typed word; the default would match on any one word
        .operator(Operator.AND);
  }

  /* see superclass */
  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }

    final boolean isSameClass = obj != null && getClass() == obj.getClass();
    if (!isSameClass) {
      return false;
    }

    final SearchAsYouType other = (SearchAsYouType) obj;
    return Objects.equals(term, other.term);
  }

  /* see superclass */
  @Override
  public int hashCode() {
    return Objects.hash(term);
  }
}
