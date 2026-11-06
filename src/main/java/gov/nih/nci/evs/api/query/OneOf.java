package gov.nih.nci.evs.api.query;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;

/**
 * Matches values exactly equal to any one of the terms (case sensitive), e.g. the codes a target
 * query found. One {@code terms} query accepts at most OpenSearch's {@code index.max_terms_count}
 * terms, 65,536 by default.
 */
public class OneOf implements ValueMatch {

  /** The terms the value may equal. */
  private Set<String> terms;

  /** Instantiates an empty {@link OneOf}. */
  public OneOf() {
    // n/a
  }

  /**
   * Instantiates a {@link OneOf}.
   *
   * @param terms the terms the value may equal
   */
  public OneOf(final Set<String> terms) {
    this.terms = new HashSet<>(Objects.requireNonNull(terms, "terms"));
  }

  /**
   * Returns the terms the value may equal.
   *
   * @return the terms
   */
  public Set<String> getTerms() {
    if (terms == null) {
      terms = new HashSet<>();
    }
    return terms;
  }

  /**
   * Sets the terms the value may equal.
   *
   * @param terms the terms the value may equal
   */
  public void setTerms(final Set<String> terms) {
    this.terms = terms;
  }

  /* see superclass */
  @Override
  public OneOf getWithTermsMapped(final UnaryOperator<String> termMapper) {
    return new OneOf(terms.stream().map(termMapper).collect(Collectors.toSet()));
  }

  /* see superclass */
  @Override
  public QueryBuilder getQueryBuilder(final String field) {
    return QueryBuilders.termsQuery(field, terms);
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

    final OneOf other = (OneOf) obj;
    return Objects.equals(terms, other.terms);
  }

  /* see superclass */
  @Override
  public int hashCode() {
    return Objects.hash(terms);
  }
}
