package gov.nih.nci.evs.api.queryendpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/**
 * Tests the AncestorOf query node, which matches the ancestors of a target concept, through the
 * query endpoint.
 */
public class AncestorOfEndpointTest extends AbstractQueryEndpointTest {

  /** The query for the parent of Female Breast, with a max level of 1. */
  private static final String PARENT_OF_FEMALE_BREAST_QUERY =
      """
      {"type": "AncestorOf",
       "targetNameOrCodeMatch": {"type": "Exact", "term": "C12851"}, "maxLevel": 1}
      """;

  /**
   * Test AncestorOf with a max level of 1 returns the target's parent, without the target.
   *
   * @throws Exception the exception
   */
  @Test
  public void testAncestorOfReturnsParent() throws Exception {
    assertThat(fetchCodes(PARENT_OF_FEMALE_BREAST_QUERY)).containsExactly(BREAST);
  }

  /**
   * Test AncestorOf with the target given as a code, not a value match, is rejected as a bad
   * request.
   *
   * @throws Exception the exception
   */
  @Test
  public void testAncestorOfWithCodeAsTargetReturnsBadRequest() throws Exception {
    final String codeAsTargetQuery =
        """
        {"type": "AncestorOf", "targetNameOrCodeMatch": "C12851"}
        """;

    postQuery(codeAsTargetQuery).andExpect(status().isBadRequest());
  }

  /**
   * Test the AncestorOf query reads from and writes back to the same JSON.
   *
   * @throws Exception the exception
   */
  @Test
  public void testAncestorOfRoundTripsUnchanged() throws Exception {
    assertRoundTripsUnchanged(PARENT_OF_FEMALE_BREAST_QUERY);
  }
}
