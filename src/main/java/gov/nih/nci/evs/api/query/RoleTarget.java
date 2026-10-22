package gov.nih.nci.evs.api.query;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.opensearch.data.core.OpenSearchOperations;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;

/**
 * The concepts a role must point to, for {@link HasRole}. Given as a query over concepts, e.g.
 * {@code Match("code", "C12470")} for Skin, or {@code Or(Match("code", "C12470"),
 * DescendantOf(Skin))} for Skin and everything below it.
 *
 * <p>A role entry only stores its target's code, so the target query describes a different concept
 * from the one being matched and can't run inside the nested roles query. Building this target runs
 * the target query first, collects the codes it matched, and matches role entries pointing to one
 * of them. A target query can hold role targets of its own, which run first in the same way.
 */
public final class RoleTarget {

  /** Field holding the code of the concept a role points to. */
  private static final String TARGET_CODE_FIELD = "roles.relatedCode";

  /** Most codes one {@code terms} query accepts: OpenSearch's default index.max_terms_count. */
  static final int MAX_TARGET_CODES = 65_536;

  /** The query selecting the target concepts. */
  private final EvsQuery query;

  /**
   * Instantiates a {@link RoleTarget}.
   *
   * @param query the query selecting the target concepts
   */
  public RoleTarget(final EvsQuery query) {
    this.query = Objects.requireNonNull(query, "query");
  }

  /**
   * Returns the query selecting the target concepts.
   *
   * @return the query
   */
  public EvsQuery getQuery() {
    return query;
  }

  /**
   * Runs the target query, then returns the query matching one role entry whose target is one of
   * the concepts it found. Only meaningful inside the nested roles query built by {@link HasRole}.
   *
   * @param operations the OpenSearch operations, for running the target query
   * @return the query builder
   */
  QueryBuilder fetchOpenSearchQuery(final OpenSearchOperations operations) {
    return QueryBuilders.termsQuery(TARGET_CODE_FIELD, fetchTargetCodes(operations));
  }

  /**
   * Runs the target query and returns the codes of the concepts it matched.
   *
   * @param operations the OpenSearch operations, for running the target query
   * @return the target codes
   * @throws IllegalStateException if there are more codes than one {@code terms} query accepts
   */
  private Set<String> fetchTargetCodes(final OpenSearchOperations operations) {
    final Set<String> targetCodes =
        EvsQuery.declareDependencies(operations, query)
            .get(0)
            .getCodesByTerminologyVersion()
            .values()
            .stream()
            .flatMap(Set::stream)
            .collect(Collectors.toSet());
    checkTargetCodeCount(targetCodes.size());
    return targetCodes;
  }

  /**
   * Fails if the target matched more concepts than one {@code terms} query accepts, rather than
   * cutting the codes short and silently giving wrong results.
   *
   * @param targetCodeCount the number of target codes
   * @throws IllegalStateException if there are too many
   */
  private static void checkTargetCodeCount(final int targetCodeCount) {
    final boolean hasTooManyTargetCodes = targetCodeCount > MAX_TARGET_CODES;
    if (hasTooManyTargetCodes) {
      throw new IllegalStateException(
          "A role target matched more than "
              + MAX_TARGET_CODES
              + " concepts, more than one OpenSearch terms query accepts. Narrow the target.");
    }
  }
}
