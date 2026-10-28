package gov.nih.nci.evs.api.query;

import gov.nih.nci.evs.api.model.Concept;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A page of a faceted search's results and its facet counts.
 *
 * @param total how many concepts match the base query and every selection
 * @param concepts the page of matching concepts
 * @param facets facet name to (value to count), as read by {@link FacetResults}
 */
public record FacetedSearchResult(
    long total, List<Concept> concepts, Map<String, Map<String, Long>> facets) {

  /**
   * Instantiates a {@link FacetedSearchResult}.
   *
   * @param total how many concepts match the base query and every selection
   * @param concepts the page of matching concepts
   * @param facets facet name to (value to count)
   */
  public FacetedSearchResult {
    concepts = List.copyOf(concepts);
    // Keeps the facets' order, which Map.copyOf would not
    facets = Collections.unmodifiableMap(new LinkedHashMap<>(facets));
  }
}
