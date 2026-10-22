package gov.nih.nci.evs.api.query;

import gov.nih.nci.evs.api.model.Concept;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The concepts a dependency query found (see {@link EvsQuery#declareDependencies}), each identified
 * by its code within its terminology version. Codes are only unique within one terminology version:
 * two versions share codes.
 */
public final class ConceptKeys {

  /** The codes found in each terminology version. */
  private final Map<TerminologyVersion, Set<String>> codesByTerminologyVersion;

  /**
   * Instantiates a {@link ConceptKeys}.
   *
   * @param codesByTerminologyVersion the codes found in each terminology version
   */
  private ConceptKeys(final Map<TerminologyVersion, Set<String>> codesByTerminologyVersion) {
    this.codesByTerminologyVersion = Map.copyOf(codesByTerminologyVersion);
  }

  /**
   * Returns the keys of the concepts, each of which has its code, terminology, and version.
   *
   * @param concepts the concepts
   * @return the keys
   */
  static ConceptKeys of(final List<Concept> concepts) {
    return new ConceptKeys(
        concepts.stream()
            .collect(
                Collectors.groupingBy(
                    TerminologyVersion::of,
                    Collectors.mapping(Concept::getCode, Collectors.toSet()))));
  }

  /**
   * Returns the codes found in each terminology version.
   *
   * @return the codes by terminology version
   */
  Map<TerminologyVersion, Set<String>> getCodesByTerminologyVersion() {
    return codesByTerminologyVersion;
  }

  /**
   * A terminology and one of its versions, e.g. ("ncit", "25.12e"). Codes are only unique within
   * one of these.
   *
   * @param terminology the terminology
   * @param version the version
   */
  record TerminologyVersion(String terminology, String version) {

    /**
     * Returns the terminology version a concept is from.
     *
     * @param concept the concept
     * @return the concept's terminology version
     */
    static TerminologyVersion of(final Concept concept) {
      return new TerminologyVersion(concept.getTerminology(), concept.getVersion());
    }
  }
}
