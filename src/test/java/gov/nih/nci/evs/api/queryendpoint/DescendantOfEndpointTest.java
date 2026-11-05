package gov.nih.nci.evs.api.queryendpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/**
 * Tests the DescendantOf node, which matches the descendants of a target concept, through the query
 * endpoint.
 */
public class DescendantOfEndpointTest extends AbstractQueryEndpointTest {

  /** The descendants of Breast at most one level down, which are its children. */
  private static final String CHILDREN_OF_BREAST_QUERY =
      """
      {"type": "DescendantOf",
       "targetNameOrCodeMatch": {"type": "Exact", "term": "C12971"}, "maxLevel": 1}
      """;

  /**
   * Test DescendantOf with a max level of 1 returns the target's children, without the target.
   *
   * @throws Exception the exception
   */
  @Test
  public void testDescendantOfReturnsChildren() throws Exception {
    assertThat(fetchCodes(CHILDREN_OF_BREAST_QUERY))
        .containsExactlyInAnyOrderElementsOf(BREAST_CHILDREN);
  }

  /**
   * Test DescendantOf with a max level that is not a number is rejected as a bad request.
   *
   * @throws Exception the exception
   */
  @Test
  public void testDescendantOfWithNonNumericMaxLevelReturnsBadRequest() throws Exception {
    final String nonNumericMaxLevelQuery =
        """
        {"type": "DescendantOf",
         "targetNameOrCodeMatch": {"type": "Exact", "term": "C12971"}, "maxLevel": "one"}
        """;

    postQuery(nonNumericMaxLevelQuery).andExpect(status().isBadRequest());
  }

  /**
   * Test DescendantOf round trips through Jackson unchanged.
   *
   * @throws Exception the exception
   */
  @Test
  public void testDescendantOfRoundTripsUnchanged() throws Exception {
    assertRoundTripsUnchanged(CHILDREN_OF_BREAST_QUERY);
  }
}
