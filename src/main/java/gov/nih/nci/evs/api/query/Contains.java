package gov.nih.nci.evs.api.query;

import java.util.Objects;
import java.util.function.UnaryOperator;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;

/** Matches values that include the term anywhere (case sensitive). */
public class Contains implements SupportsIgnoringCase {

  /** Wildcard that matches any sequence of characters. */
  private static final String ANY_CHARACTERS = "*";

  /** The substring the value must include. */
  private String term;

  /** Instantiates an empty {@link Contains}. */
  public Contains() {
    // n/a
  }

  /**
   * Instantiates a {@link Contains}.
   *
   * @param term the substring the value must include
   */
  public Contains(final String term) {
    this.term = Objects.requireNonNull(term, "term");
  }

  /**
   * Returns the substring the value must include.
   *
   * @return the term
   */
  public String getTerm() {
    return term;
  }

  /**
   * Sets the substring the value must include.
   *
   * @param term the substring the value must include
   */
  public void setTerm(final String term) {
    this.term = term;
  }

  /* see superclass */
  @Override
  public Contains getWithTermsMapped(final UnaryOperator<String> termMapper) {
    return new Contains(termMapper.apply(term));
  }

  /* see superclass */
  @Override
  public QueryBuilder getQueryBuilder(final String field) {
    return QueryBuilders.wildcardQuery(field, getPattern());
  }

  /* see superclass */
  @Override
  public QueryBuilder getCaseInsensitiveQueryBuilder(final String field) {
    return QueryBuilders.wildcardQuery(field, getPattern()).caseInsensitive(true);
  }

  /**
   * Returns the wildcard pattern that matches the term anywhere in a value.
   *
   * @return the pattern
   */
  private String getPattern() {
    return ANY_CHARACTERS + escapeWildcards(term) + ANY_CHARACTERS;
  }

  /**
   * Escapes wildcard syntax so the term is matched literally.
   *
   * @param value the raw term
   * @return the escaped term
   */
  private static String escapeWildcards(final String value) {
    // Escape the escape character first so later escapes are not doubled
    return value.replace("\\", "\\\\").replace("*", "\\*").replace("?", "\\?");
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

    final Contains other = (Contains) obj;
    return Objects.equals(term, other.term);
  }

  /* see superclass */
  @Override
  public int hashCode() {
    return Objects.hash(term);
  }
}
