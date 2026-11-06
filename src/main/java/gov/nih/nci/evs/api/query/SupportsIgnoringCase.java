package gov.nih.nci.evs.api.query;

import java.util.function.UnaryOperator;
import org.opensearch.index.query.QueryBuilder;

/** A match that can also ignore case. Required to be wrapped by {@link CaseInsensitive}. */
public interface SupportsIgnoringCase extends ValueMatch {

  /**
   * Returns {@link #getQueryBuilder} with case ignored.
   *
   * @param field the field holding the value
   * @return the case-insensitive query builder
   */
  QueryBuilder getCaseInsensitiveQueryBuilder(String field);

  /* see superclass */
  @Override
  SupportsIgnoringCase getWithTermsMapped(UnaryOperator<String> termMapper);
}
