package gov.nih.nci.evs.api.query;

import java.util.Objects;

/**
 * The concepts a role must point to, for {@link HasRole}. Given as a query over concepts, e.g.
 * {@code Match("code", "C12470")} for Skin.
 *
 * <p>A role entry only stores its target's code, so the target query describes a different concept
 * from the one being matched and can't run inside the role query. {@link HasRole} runs it first
 * (see {@link EvsQuery#declareDependencies}) and matches the concepts it found. A target query can
 * hold role targets of its own, which run first in the same way.
 */
public final class RoleTarget {

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
}
