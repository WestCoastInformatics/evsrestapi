package gov.nih.nci.evs.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import gov.nih.nci.evs.api.model.Concept;
import gov.nih.nci.evs.api.model.ConceptResultList;
import gov.nih.nci.evs.api.util.ThreadLocalMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Scratch comparison, not a regression test: prints typeahead suggestions next to the existing
 * startsWith search so their rankings can be compared by eye. It makes no assertions and always
 * passes, so it should not be committed.
 *
 * <p>For each sample input it prints the top five results of:
 *
 * <ul>
 *   <li>{@code CURRENT}: the new typeahead, {@link SearchController#typeaheadService}.
 *   <li>{@code NCIT-ONLY}: the existing {@code /api/v1/concept/search} endpoint with {@code
 *       type=startsWith}, limited to ncit.
 * </ul>
 *
 * <p>Every printed line starts with {@code EXAMPLE>>} so the output can be pulled out of the test
 * log, e.g. {@code ./gradlew test --tests '*TmpTypeaheadExamplesTest' -i | grep 'EXAMPLE>>'}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public class TmpTypeaheadExamplesTest {
  /** The mock MVC, used to call the existing search endpoint. */
  @Autowired MockMvc mvc;

  /** The search controller, used to call the typeahead directly. */
  @Autowired SearchController searchController;

  /**
   * Prints a result list: a header line with the label and total, then one line per concept with
   * its terminology, code, and name.
   *
   * @param label describes which search produced the results
   * @param l the results to print
   */
  private void print(String label, ConceptResultList l) {
    System.out.println("EXAMPLE>> " + label + " total=" + l.getTotal());
    for (Concept c : l.getConcepts()) {
      System.out.println(
          "EXAMPLE>>    " + c.getTerminology() + " " + c.getCode() + " " + c.getName());
    }
  }

  /**
   * Prints the typeahead and startsWith results for each sample input. The inputs cover a partial
   * word ("melan"), a common word ("cancer"), a partial code ("C322"), a partial last word ("lung
   * ca"), a gene and mutation ("braf v6"), and text that matches nothing ("xyzzq").
   *
   * @throws Exception the exception
   */
  @Test
  public void examples() throws Exception {
    for (String t : new String[] {"melan", "cancer", "C322", "lung ca", "braf v6", "xyzzq"}) {
      print(
          "CURRENT typeaheadService(\"" + t + "\", pageSize=5)",
          searchController.typeaheadService(t, null, null, 5));
      String body =
          mvc.perform(
                  get("/api/v1/concept/search")
                      .param("terminology", "ncit")
                      .param("type", "startsWith")
                      .param("term", t)
                      .param("pageSize", "5"))
              .andReturn()
              .getResponse()
              .getContentAsString();
      print(
          "NCIT-ONLY startsWith(\"" + t + "\", pageSize=5)",
          ThreadLocalMapper.get().readValue(body, ConceptResultList.class));
    }
  }
}
