package gov.nih.nci.evs.api.query;

import java.util.Objects;
import java.util.function.UnaryOperator;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;

/** Matches values that begin with the term (case sensitive). */
public class StartsWith implements SupportsIgnoringCase {

  /** The prefix the value must begin with. */
  private String term;

  /** Instantiates an empty {@link StartsWith}. */
  public StartsWith() {
    // n/a
  }

  /**
   * Instantiates a {@link StartsWith}.
   *
   * @param term the prefix the value must begin with
   */
  public StartsWith(final String term) {
    this.term = Objects.requireNonNull(term, "term");
  }

  /**
   * Returns the prefix the value must begin with.
   *
   * @return the term
   */
  public String getTerm() {
    return term;
  }

  /**
   * Sets the prefix the value must begin with.
   *
   * @param term the prefix the value must begin with
   */
  public void setTerm(final String term) {
    this.term = term;
  }

  /* see superclass */
  @Override
  public StartsWith getWithTermsMapped(final UnaryOperator<String> termMapper) {
    return new StartsWith(termMapper.apply(term));
  }

  /* see superclass */
  @Override
  public QueryBuilder getQueryBuilder(final String field) {
    return QueryBuilders.prefixQuery(field, term);
  }

  /* see superclass */
  @Override
  public QueryBuilder getCaseInsensitiveQueryBuilder(final String field) {
    return QueryBuilders.prefixQuery(field, term).caseInsensitive(true);
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

    final StartsWith other = (StartsWith) obj;
    return Objects.equals(term, other.term);
  }

  /* see superclass */
  @Override
  public int hashCode() {
    return Objects.hash(term);
  }
}
