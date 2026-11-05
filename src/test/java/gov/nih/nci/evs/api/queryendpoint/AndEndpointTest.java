package gov.nih.nci.evs.api.queryendpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/**
 * Tests And, which matches the concepts every one of its children matches, through the query
 * endpoint.
 */
public class AndEndpointTest extends AbstractQueryEndpointTest {

  /** The children of Breast that have a synonym starting "left", combined with And. */
  private static final String LEFT_CHILD_OF_BREAST_QUERY =
      """
      {"type": "And", "children": [
        {"type": "DescendantOf",
         "targetNameOrCodeMatch": {"type": "Exact", "term": "C12971"}, "maxLevel": 1},
        {"type": "HasSynonym",
         "normalizedNameMatch": {"type": "StartsWith", "term": "left"}}]}
      """;

  /**
   * Test And returns only the concepts every child matches: the child of Breast with a synonym
   * starting "left".
   *
   * @throws Exception the exception
   */
  @Test
  public void testAndReturnsConceptsMatchingEveryChild() throws Exception {
    assertThat(fetchCodes(LEFT_CHILD_OF_BREAST_QUERY)).containsExactly(LEFT_BREAST);
  }

  /**
   * Test And with a misspelled children property (child, not children) is rejected as a bad
   * request.
   *
   * @throws Exception the exception
   */
  @Test
  public void testAndWithMisspelledChildrenPropertyReturnsBadRequest() throws Exception {
    final String misspelledChildrenQuery =
        """
        {"type": "And", "child": [
          {"type": "HasRole", "typeNameOrCodeMatch": {"type": "Exact", "term": "R101"}}]}
        """;

    postQuery(misspelledChildrenQuery).andExpect(status().isBadRequest());
  }

  /**
   * Test And round trips unchanged through Jackson.
   *
   * @throws Exception the exception
   */
  @Test
  public void testAndRoundTripsUnchanged() throws Exception {
    assertRoundTripsUnchanged(LEFT_CHILD_OF_BREAST_QUERY);
  }
}
