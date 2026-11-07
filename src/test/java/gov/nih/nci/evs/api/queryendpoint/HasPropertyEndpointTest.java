package gov.nih.nci.evs.api.queryendpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gov.nih.nci.evs.api.query.AllQualifiers;
import gov.nih.nci.evs.api.query.AnyQualifier;
import gov.nih.nci.evs.api.query.EvsQuery;
import gov.nih.nci.evs.api.query.HasProperty;
import gov.nih.nci.evs.api.query.NoQualifier;
import gov.nih.nci.evs.api.query.PropertyQualifier;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests HasProperty, which matches the concepts with a property entry matching its type, value and
 * qualifiers, through the query endpoint.
 */
public class HasPropertyEndpointTest extends AbstractQueryEndpointTest {

  /**
   * The nucleotide-excision repair GO annotations with go-id GO:0006289 whose evidence is neither
   * TAS nor NR.
   */
  private static final String REPAIR_ANNOTATION_WITHOUT_TAS_OR_NR_QUERY =
      """
      {"type": "HasProperty", "property": "P211", "value": "nucleotide-excision repair",
       "qualifiers": {"type": "AllQualifiers", "conditions": [
         {"type": "PropertyQualifier", "qualifier": "P387", "value": "GO:0006289"},
         {"type": "NoQualifier", "condition": {"type": "AnyQualifier", "conditions": [
           {"type": "PropertyQualifier", "qualifier": "P389", "value": "TAS"},
           {"type": "PropertyQualifier", "qualifier": "P389", "value": "NR"}]}}]}}
      """;

  /**
   * Test HasProperty with qualifiers returns the concepts with a matching property entry: the
   * nucleotide-excision repair GO annotations with go-id GO:0006289 whose evidence is neither TAS
   * nor NR. Two such annotations have NAS evidence; the other five, with TAS or NR, are left out.
   *
   * @throws Exception the exception
   */
  @Test
  public void testHasPropertyWithQualifiersReturnsMatchingConcepts() throws Exception {
    // ERCC2 Gene and ERCC2 wt Allele
    assertThat(fetchCodes(REPAIR_ANNOTATION_WITHOUT_TAS_OR_NR_QUERY))
        .containsExactlyInAnyOrder("C20657", "C50981");
  }

  /**
   * Test HasProperty with a misspelled qualifier type (AllQualifier) is rejected as a bad request.
   *
   * @throws Exception the exception
   */
  @Test
  public void testHasPropertyWithMisspelledQualifierTypeReturnsBadRequest() throws Exception {
    final String misspelledQualifierTypeQuery =
        """
        {"type": "HasProperty", "property": "P211",
         "qualifiers": {"type": "AllQualifier", "conditions": []}}
        """;

    postQuery(misspelledQualifierTypeQuery).andExpect(status().isBadRequest());
  }

  /**
   * Test HasProperty round trips unchanged through Jackson.
   *
   * @throws Exception the exception
   */
  @Test
  public void testHasPropertyRoundTripsUnchanged() throws Exception {
    assertRoundTripsUnchanged(REPAIR_ANNOTATION_WITHOUT_TAS_OR_NR_QUERY);
  }

  /**
   * Test HasProperty serializes to the expected JSON when built in Java: the HasProperty with
   * qualifiers.
   *
   * @throws Exception the exception
   */
  @Test
  public void testHasPropertySerializesToExpectedJson() throws Exception {
    final EvsQuery repairAnnotationWithoutTasOrNr =
        new HasProperty(
            "P211",
            "nucleotide-excision repair",
            new AllQualifiers(
                List.of(
                    new PropertyQualifier("P387", "GO:0006289"),
                    new NoQualifier(
                        new AnyQualifier(
                            List.of(
                                new PropertyQualifier("P389", "TAS"),
                                new PropertyQualifier("P389", "NR")))))));

    assertSerializesTo(repairAnnotationWithoutTasOrNr, REPAIR_ANNOTATION_WITHOUT_TAS_OR_NR_QUERY);
  }
}
