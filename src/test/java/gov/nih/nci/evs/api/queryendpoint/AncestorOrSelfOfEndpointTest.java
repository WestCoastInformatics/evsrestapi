package gov.nih.nci.evs.api.queryendpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gov.nih.nci.evs.api.query.AncestorOrSelfOf;
import gov.nih.nci.evs.api.query.Exact;
import org.junit.jupiter.api.Test;

/**
 * Tests the AncestorOrSelfOf query node, which matches a target concept and its ancestors, through
 * the query endpoint.
 */
public class AncestorOrSelfOfEndpointTest extends AbstractQueryEndpointTest {

  /** The query for Female Breast and its parent, with a max level of 1. */
  private static final String FEMALE_BREAST_AND_PARENT_QUERY =
      """
      {"type": "AncestorOrSelfOf",
       "targetNameOrCodeMatch": {"type": "Exact", "term": "C12851"}, "maxLevel": 1}
      """;

  /**
   * Test AncestorOrSelfOf with a max level of 1 returns the target and its parent.
   *
   * @throws Exception the exception
   */
  @Test
  public void testAncestorOrSelfOfReturnsTargetAndParent() throws Exception {
    assertThat(fetchCodes(FEMALE_BREAST_AND_PARENT_QUERY))
        .containsExactlyInAnyOrder(FEMALE_BREAST, BREAST);
  }

  /**
   * Test AncestorOrSelfOf with a fuzziness OpenSearch doesn't allow (THREE) is rejected as a bad
   * request.
   *
   * @throws Exception the exception
   */
  @Test
  public void testAncestorOrSelfOfWithUnsupportedFuzzinessReturnsBadRequest() throws Exception {
    final String unsupportedFuzzinessQuery =
        """
        {"type": "AncestorOrSelfOf",
         "targetNameOrCodeMatch": {"type": "Fuzzy", "term": "C12851", "fuzziness": "THREE"}}
        """;

    postQuery(unsupportedFuzzinessQuery).andExpect(status().isBadRequest());
  }

  /**
   * Test the AncestorOrSelfOf query reads from and writes back to the same JSON.
   *
   * @throws Exception the exception
   */
  @Test
  public void testAncestorOrSelfOfRoundTripsUnchanged() throws Exception {
    assertRoundTripsUnchanged(FEMALE_BREAST_AND_PARENT_QUERY);
  }

  /**
   * Test the AncestorOrSelfOf query built in Java is written as the expected JSON.
   *
   * @throws Exception the exception
   */
  @Test
  public void testAncestorOrSelfOfSerializesToExpectedJson() throws Exception {
    final AncestorOrSelfOf femaleBreastAndParent =
        new AncestorOrSelfOf(new Exact(FEMALE_BREAST), DIRECT_RELATIVES_MAX_LEVEL);

    assertSerializesTo(femaleBreastAndParent, FEMALE_BREAST_AND_PARENT_QUERY);
  }
}
