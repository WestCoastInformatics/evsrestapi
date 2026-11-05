package gov.nih.nci.evs.api.queryendpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gov.nih.nci.evs.api.query.DescendantOrSelfOf;
import gov.nih.nci.evs.api.query.Exact;
import org.junit.jupiter.api.Test;

/**
 * Tests the DescendantOrSelfOf node, which matches a target concept and its descendants, through
 * the query endpoint.
 */
public class DescendantOrSelfOfEndpointTest extends AbstractQueryEndpointTest {

  /** Breast and its descendants at most one level down, which are its children. */
  private static final String BREAST_AND_CHILDREN_QUERY =
      """
      {"type": "DescendantOrSelfOf",
       "targetNameOrCodeMatch": {"type": "Exact", "term": "C12971"}, "maxLevel": 1}
      """;

  /**
   * Test DescendantOrSelfOf with a max level of 1 returns the target and its children.
   *
   * @throws Exception the exception
   */
  @Test
  public void testDescendantOrSelfOfReturnsTargetAndChildren() throws Exception {
    assertThat(fetchCodes(BREAST_AND_CHILDREN_QUERY))
        .containsExactlyInAnyOrderElementsOf(getWithBreast(BREAST_CHILDREN));
  }

  /**
   * Test DescendantOrSelfOf with a misspelled node type (DescendantOrSelf) is rejected as a bad
   * request.
   *
   * @throws Exception the exception
   */
  @Test
  public void testDescendantOrSelfOfWithMisspelledNodeTypeReturnsBadRequest() throws Exception {
    final String misspelledNodeTypeQuery =
        """
        {"type": "DescendantOrSelf",
         "targetNameOrCodeMatch": {"type": "Exact", "term": "C12971"}}
        """;

    postQuery(misspelledNodeTypeQuery).andExpect(status().isBadRequest());
  }

  /**
   * Test DescendantOrSelfOf round trips through Jackson unchanged.
   *
   * @throws Exception the exception
   */
  @Test
  public void testDescendantOrSelfOfRoundTripsUnchanged() throws Exception {
    assertRoundTripsUnchanged(BREAST_AND_CHILDREN_QUERY);
  }

  /**
   * Test DescendantOrSelfOf serializes to the expected JSON, the wire format clients use.
   *
   * @throws Exception the exception
   */
  @Test
  public void testDescendantOrSelfOfSerializesToExpectedJson() throws Exception {
    final DescendantOrSelfOf breastAndChildren =
        new DescendantOrSelfOf(new Exact(BREAST), DIRECT_RELATIVES_MAX_LEVEL);

    assertSerializesTo(breastAndChildren, BREAST_AND_CHILDREN_QUERY);
  }
}
