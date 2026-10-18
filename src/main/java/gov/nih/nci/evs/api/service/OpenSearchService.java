package gov.nih.nci.evs.api.service;

import gov.nih.nci.evs.api.model.ConceptResultList;
import gov.nih.nci.evs.api.model.Mapping;
import gov.nih.nci.evs.api.model.MappingResultList;
import gov.nih.nci.evs.api.model.SearchCriteria;
import gov.nih.nci.evs.api.model.Terminology;
import java.util.List;
import org.springframework.web.client.HttpClientErrorException;

/** The Interface OpenSearchService. */
public interface OpenSearchService {

  /**
   * Search.
   *
   * @param terminologies the terminologies
   * @param searchCriteria the search criteria
   * @return the string
   * @throws Exception the exception
   * @throws HttpClientErrorException the http client error exception
   */
  public ConceptResultList findConcepts(
      List<Terminology> terminologies, SearchCriteria searchCriteria) throws Exception;

  /**
   * Finds concepts whose preferred names complete a partially typed term: every typed word must
   * appear, in any order, with the last word allowed to be partial. General concepts with short
   * names come first, with a small preference for names that start with the typed text.
   *
   * @param terminologies the terminologies to search
   * @param searchCriteria the search criteria; its term is the partially typed term, and it also
   *     supplies the page size and included fields
   * @return the first page of matching concepts, best match first
   */
  public ConceptResultList findTypeaheadConcepts(
      List<Terminology> terminologies, SearchCriteria searchCriteria);

  /**
   * search for the given search criteria in mappings.
   *
   * @param query the query to search for the mappings
   * @param searchCriteria the search criteria
   * @return the result list with concepts
   */
  MappingResultList findConceptMappings(String query, SearchCriteria searchCriteria);

  /**
   * Returns the term query for mappings based on concepts.
   *
   * @param asList the concept codes
   * @param terminology the terminology
   * @return the mappings
   */
  public List<Mapping> getConceptMappings(List<String> asList, String terminology);
}
