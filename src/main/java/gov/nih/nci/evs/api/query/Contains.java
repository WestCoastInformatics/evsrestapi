package gov.nih.nci.evs.api.query;

import gov.nih.nci.evs.api.model.Terminology;
import java.util.List;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;

/** Matches values that include the term anywhere (case sensitive). */
public class Contains extends AtomicQuery {

  /** Wildcard that matches any sequence of characters. */
  private static final String ANY_CHARACTERS = "*";

  /**
   * Instantiates a {@link Contains}.
   *
   * @param terminologies the terminologies being searched
   * @param field the field to match against
   * @param term the substring the value must include
   */
  public Contains(final List<Terminology> terminologies, final String field, final String term) {
    super(terminologies, field, term);
  }

  /* see superclass */
  @Override
  public QueryBuilder fetchOpenSearchQuery() {
    final String pattern = ANY_CHARACTERS + escapeWildcards(term) + ANY_CHARACTERS;
    return QueryBuilders.wildcardQuery(field, pattern);
  }

  /**
   * Escapes wildcard syntax so the term is matched literally.
   *
   * @param value the raw term
   * @return the escaped term
   */
  static String escapeWildcards(final String value) {
    // Escape the escape character first so later escapes are not doubled
    return value.replace("\\", "\\\\").replace("*", "\\*").replace("?", "\\?");
  }
}
