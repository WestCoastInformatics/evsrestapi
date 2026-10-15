package gov.nih.nci.evs.api.query;

import org.opensearch.index.query.QueryBuilder;

/**
 * A query that can also be run ignoring case. Required to be wrapped by {@link CaseInsensitive}.
 */
public interface SupportsIgnoringCase {

  /**
   * Returns the OpenSearch query for this node with case ignored.
   *
   * @return the case-insensitive query builder
   */
  QueryBuilder fetchCaseInsensitiveOpenSearchQuery();
}
