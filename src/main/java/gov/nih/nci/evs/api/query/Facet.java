package gov.nih.nci.evs.api.query;

import java.util.Objects;
import java.util.Optional;

/**
 * One facet to show in a faceted search: which dimension to count, and what the user picked in it.
 *
 * <p>The dimension is a node used only for its facet ({@link EvsQuery#fetchFacetBuilders}); its own
 * condition doesn't filter anything. E.g. {@code new HasProperty(t, new Exact("Semantic_Type"))}
 * counts semantic types. The selection is the user's picks in that facet, if any: one node, or an
 * {@link Or} of several (several picks in one facet widen the results).
 *
 * @param dimension the node whose facet is counted
 * @param selection the user's picks in this facet, if any
 */
public record Facet(EvsQuery dimension, Optional<EvsQuery> selection) {

  /**
   * Instantiates a {@link Facet}.
   *
   * @param dimension the node whose facet is counted
   * @param selection the user's picks in this facet, if any
   */
  public Facet {
    Objects.requireNonNull(dimension, "dimension");
    Objects.requireNonNull(selection, "selection");
  }

  /**
   * Returns a facet with nothing picked yet.
   *
   * @param dimension the node whose facet is counted
   * @return the facet
   */
  public static Facet of(final EvsQuery dimension) {
    return new Facet(dimension, Optional.empty());
  }

  /**
   * Returns a facet with the user's picks.
   *
   * @param dimension the node whose facet is counted
   * @param selection the user's picks in this facet
   * @return the facet
   */
  public static Facet of(final EvsQuery dimension, final EvsQuery selection) {
    return new Facet(dimension, Optional.of(selection));
  }
}
