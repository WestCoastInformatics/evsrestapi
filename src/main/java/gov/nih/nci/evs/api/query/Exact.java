package gov.nih.nci.evs.api.query;

import java.util.Objects;
import java.util.function.UnaryOperator;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;

/** Matches values exactly equal to the term (case sensitive). */
public class Exact implements SupportsIgnoringCase {

  /** The term the value must equal. */
  private String term;

  /** Instantiates an empty {@link Exact}. */
  public Exact() {
    // n/a
  }

  /**
   * Instantiates an {@link Exact}.
   *
   * @param term the term the value must equal
   */
  public Exact(final String term) {
    this.term = Objects.requireNonNull(term, "term");
  }

  /**
   * Returns the term the value must equal.
   *
   * @return the term
   */
  public String getTerm() {
    return term;
  }

  /**
   * Sets the term the value must equal.
   *
   * @param term the term the value must equal
   */
  public void setTerm(final String term) {
    this.term = term;
  }

  /* see superclass */
  @Override
  public Exact getWithTermsMapped(final UnaryOperator<String> termMapper) {
    return new Exact(termMapper.apply(term));
  }

  /* see superclass */
  @Override
  public QueryBuilder getQueryBuilder(final String field) {
    return QueryBuilders.termQuery(field, term);
  }

  /* see superclass */
  @Override
  public QueryBuilder getCaseInsensitiveQueryBuilder(final String field) {
    return QueryBuilders.termQuery(field, term).caseInsensitive(true);
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

    final Exact other = (Exact) obj;
    return Objects.equals(term, other.term);
  }

  /* see superclass */
  @Override
  public int hashCode() {
    return Objects.hash(term);
  }
}
