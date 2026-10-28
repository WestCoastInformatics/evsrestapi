package gov.nih.nci.evs.api.query;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.opensearch.search.aggregations.bucket.terms.TermsAggregationBuilder;

/** Unit tests for {@link FacetAggregations}. Needs no services. */
public class FacetAggregationsTest {

  /** A facet name joins the dimension and the key. */
  @Test
  public void testFacetNameJoinsDimensionAndKey() {
    assertEquals("roles:R101", FacetAggregations.getFacetName("roles", "R101"));
  }

  /** Characters OpenSearch doesn't allow in aggregation names are replaced. */
  @Test
  public void testFacetNameReplacesDisallowedCharacters() {
    assertEquals("x:a_b_c_", FacetAggregations.getFacetName("x", "a[b]c>"));
  }

  /** An exact match's key is its term. */
  @Test
  public void testExactMatchKeyIsItsTerm() {
    assertEquals("R101", FacetAggregations.getMatchKey(new Exact("R101")));
  }

  /** A one-of match's key lists its terms in order, so equal matches give equal names. */
  @Test
  public void testOneOfMatchKeyListsTermsInOrder() {
    assertEquals("A2,A8", FacetAggregations.getMatchKey(new OneOf(Set.of("A8", "A2"))));
  }

  /** A codes key lists the codes in order. */
  @Test
  public void testCodesKeyListsCodesInOrder() {
    assertEquals("C1,C2,C3", FacetAggregations.getCodesKey(List.of("C3", "C1", "C2")));
  }

  /** A terms facet returns at most the cap. */
  @Test
  public void testTermsFacetIsCapped() {
    final TermsAggregationBuilder facet =
        (TermsAggregationBuilder) FacetAggregations.getTermsFacet("status", "conceptStatus");

    assertEquals(FacetAggregations.MAX_FACET_VALUES, facet.size());
  }
}
