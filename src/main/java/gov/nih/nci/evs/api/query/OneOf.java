package gov.nih.nci.evs.api.query;

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
 *
 * @param terms the terms the value may equal
 */
public record OneOf(Set<String> terms) implements ValueMatch {

  /**
   * Instantiates a {@link OneOf}.
   *
   * @param terms the terms the value may equal
   */
  public OneOf {
    terms = Set.copyOf(Objects.requireNonNull(terms, "terms"));
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
}
