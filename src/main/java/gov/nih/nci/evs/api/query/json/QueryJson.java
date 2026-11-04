package gov.nih.nci.evs.api.query.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import gov.nih.nci.evs.api.model.Terminology;
import gov.nih.nci.evs.api.query.EvsQuery;
import java.util.List;

/**
 * Converts query trees to and from JSON, the request format of the composable search endpoint.
 *
 * <p>This branch keeps JSON out of the query nodes with DTOs: Jackson reads and writes the records
 * in this package ({@link QueryDto} and the rest), which convert to and from the nodes. Each JSON
 * object names its record with a lower-camel-case {@code type}; records nest, so trees do too:
 *
 * <pre>{@code
 * {"type":"and","children":[
 *   {"type":"descendantOf","code":"C3262","maxLevel":3},
 *   {"type":"hasRole","relationshipType":{"type":"exact","term":"R101"},
 *    "target":{"query":{"type":"descendantOrSelfOf","code":"C12470"}}}]}
 * }</pre>
 *
 * <p>The JSON never names terminologies: the request does (one terminology per request, from the
 * path), and {@link #readQuery} gives them to every node it builds. Unknown types and properties
 * fail rather than being dropped.
 */
public final class QueryJson {

  /** The property naming each JSON object's node, match, or condition type. */
  static final String TYPE_PROPERTY = "type";

  /** The mapper. Thread safe once built. Leaves out null properties, e.g. a missing target. */
  private static final ObjectMapper MAPPER =
      JsonMapper.builder()
          .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .defaultPropertyInclusion(JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, null))
          .build();

  /** Only static helpers. */
  private QueryJson() {}

  /**
   * Builds a query tree from its JSON form.
   *
   * @param json the JSON
   * @param terminologies the terminologies being searched, given to every node
   * @return the query tree
   * @throws IllegalArgumentException if the JSON isn't a valid query, with the reason
   */
  public static EvsQuery readQuery(final String json, final List<Terminology> terminologies) {
    final QueryDto query;
    try {
      query = MAPPER.readValue(json, QueryDto.class);
    } catch (final JsonProcessingException e) {
      throw new IllegalArgumentException("Invalid query JSON: " + getReason(e), e);
    }

    return query.toQuery(List.copyOf(terminologies));
  }

  /**
   * Writes a query tree as JSON.
   *
   * @param query the query tree
   * @return the JSON
   * @throws IllegalArgumentException if a node in the tree has no JSON form
   */
  public static String writeQuery(final EvsQuery query) {
    final QueryDto queryDto = QueryDtos.getQueryDto(query);
    try {
      return MAPPER.writeValueAsString(queryDto);
    } catch (final JsonProcessingException e) {
      throw new IllegalArgumentException("Query can't be written as JSON: " + getReason(e), e);
    }
  }

  /**
   * Returns why a JSON read or write failed. A record's own check (e.g. both a code and an anchor)
   * is the cause of Jackson's error, and is clearer than Jackson's wrapper message.
   *
   * @param e the error
   * @return the reason
   */
  private static String getReason(final JsonProcessingException e) {
    final boolean isRecordCheck = e.getCause() instanceof IllegalArgumentException;
    return isRecordCheck ? e.getCause().getMessage() : e.getOriginalMessage();
  }
}
