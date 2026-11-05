package gov.nih.nci.evs.api.queryendpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gov.nih.nci.evs.api.query.AncestorOf;
import gov.nih.nci.evs.api.query.DescendantOf;
import gov.nih.nci.evs.api.query.EvsQuery;
import gov.nih.nci.evs.api.query.Exact;
import gov.nih.nci.evs.api.query.Or;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests Or, which matches the concepts any one of its children matches, through the query endpoint.
 */
public class OrEndpointTest extends AbstractQueryEndpointTest {

  /** The children of Breast or the parent of Female Breast, combined with Or. */
  private static final String CHILDREN_OR_PARENT_QUERY =
      """
      {"type": "Or", "children": [
        {"type": "DescendantOf",
         "target": {"type": "Exact", "term": "C12971"}, "maxLevel": 1},
        {"type": "AncestorOf",
         "target": {"type": "Exact", "term": "C12851"}, "maxLevel": 1}]}
      """;

  /**
   * Test Or returns the concepts any child matches: the children of Breast, and the parent of
   * Female Breast, which is Breast.
   *
   * @throws Exception the exception
   */
  @Test
  public void testOrReturnsConceptsMatchingAnyChild() throws Exception {
    assertThat(fetchCodes(CHILDREN_OR_PARENT_QUERY))
        .containsExactlyInAnyOrderElementsOf(getWithBreast(BREAST_CHILDREN));
  }

  /**
   * Test Or with children not in a list is rejected as a bad request.
   *
   * @throws Exception the exception
   */
  @Test
  public void testOrWithChildrenNotInListReturnsBadRequest() throws Exception {
    final String childrenNotInListQuery =
        """
        {"type": "Or", "children":
          {"type": "HasRole", "role": {"type": "Exact", "term": "R101"}}}
        """;

    postQuery(childrenNotInListQuery).andExpect(status().isBadRequest());
  }

  /**
   * Test Or round trips unchanged through Jackson.
   *
   * @throws Exception the exception
   */
  @Test
  public void testOrRoundTripsUnchanged() throws Exception {
    assertRoundTripsUnchanged(CHILDREN_OR_PARENT_QUERY);
  }

  /**
   * Test Or serializes to the expected JSON when built in Java: the Or of the children of Breast
   * and the parent of Female Breast.
   *
   * @throws Exception the exception
   */
  @Test
  public void testOrSerializesToExpectedJson() throws Exception {
    final EvsQuery childrenOrParent =
        new Or(
            List.of(
                new DescendantOf(new Exact(BREAST), DIRECT_RELATIVES_MAX_LEVEL),
                new AncestorOf(new Exact(FEMALE_BREAST), DIRECT_RELATIVES_MAX_LEVEL)));

    assertSerializesTo(childrenOrParent, CHILDREN_OR_PARENT_QUERY);
  }
}
