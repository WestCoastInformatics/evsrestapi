package gov.nih.nci.evs.api.query;

import gov.nih.nci.evs.api.model.Terminology;
import java.util.List;
import org.opensearch.data.core.OpenSearchOperations;
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
public class SearchAsYouType extends AtomicQuery {

  /** Subfield holding adjacent word pairs, created by the search_as_you_type mapping. */
  private static final String WORD_PAIRS_SUFFIX = "._2gram";

  /** Subfield holding adjacent word triples, created by the search_as_you_type mapping. */
  private static final String WORD_TRIPLES_SUFFIX = "._3gram";

  /**
   * Instantiates a {@link SearchAsYouType}.
   *
   * @param terminologies the terminologies being searched
   * @param field the search_as_you_type field to match against
   * @param term the text typed so far
   */
  public SearchAsYouType(
      final List<Terminology> terminologies, final String field, final String term) {
    super(terminologies, field, term);
  }

  /* see superclass */
  @Override
  public QueryBuilder fetchOpenSearchQuery(final OpenSearchOperations operations) {
    return QueryBuilders.multiMatchQuery(
            term, field, field + WORD_PAIRS_SUFFIX, field + WORD_TRIPLES_SUFFIX)
        .type(MultiMatchQueryBuilder.Type.BOOL_PREFIX)
        // Require every typed word; the default would match on any one word
        .operator(Operator.AND);
  }
}
