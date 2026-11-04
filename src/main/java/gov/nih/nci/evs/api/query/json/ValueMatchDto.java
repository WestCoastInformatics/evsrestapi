package gov.nih.nci.evs.api.query.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import gov.nih.nci.evs.api.query.CaseInsensitive;
import gov.nih.nci.evs.api.query.Contains;
import gov.nih.nci.evs.api.query.Exact;
import gov.nih.nci.evs.api.query.Fuzzy;
import gov.nih.nci.evs.api.query.OneOf;
import gov.nih.nci.evs.api.query.SearchAsYouType;
import gov.nih.nci.evs.api.query.StartsWith;
import gov.nih.nci.evs.api.query.SupportsIgnoringCase;
import gov.nih.nci.evs.api.query.ValueMatch;
import java.util.Set;
import org.opensearch.common.unit.Fuzziness;

/**
 * The JSON form of a {@link ValueMatch}: one record per match type, picked by the {@code type}
 * property like {@link QueryDto}. {@link IgnoringCaseDto} mirrors {@link SupportsIgnoringCase}, so
 * {@code caseInsensitive} only accepts the matches that can ignore case.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = QueryJson.TYPE_PROPERTY)
@JsonSubTypes({
  @JsonSubTypes.Type(value = ValueMatchDto.ExactDto.class, name = "exact"),
  @JsonSubTypes.Type(value = ValueMatchDto.ContainsDto.class, name = "contains"),
  @JsonSubTypes.Type(value = ValueMatchDto.StartsWithDto.class, name = "startsWith"),
  @JsonSubTypes.Type(value = ValueMatchDto.FuzzyDto.class, name = "fuzzy"),
  @JsonSubTypes.Type(value = ValueMatchDto.SearchAsYouTypeDto.class, name = "searchAsYouType"),
  @JsonSubTypes.Type(value = ValueMatchDto.OneOfDto.class, name = "oneOf"),
  @JsonSubTypes.Type(value = ValueMatchDto.CaseInsensitiveDto.class, name = "caseInsensitive")
})
public sealed interface ValueMatchDto {

  /**
   * Builds the value match.
   *
   * @return the match
   */
  ValueMatch toValueMatch();

  /**
   * Builds the value match of an optional record.
   *
   * @param match the record, or null
   * @return the match, or null
   */
  static ValueMatch toValueMatchOrNull(final ValueMatchDto match) {
    final boolean hasMatch = match != null;
    return hasMatch ? match.toValueMatch() : null;
  }

  /** A match that can also ignore case: {@link SupportsIgnoringCase}. */
  sealed interface IgnoringCaseDto extends ValueMatchDto {

    /* see superclass */
    @Override
    SupportsIgnoringCase toValueMatch();
  }

  /**
   * {@link Exact}.
   *
   * @param term the term the value must equal
   */
  record ExactDto(@JsonProperty(required = true) String term) implements IgnoringCaseDto {

    /* see superclass */
    @Override
    public Exact toValueMatch() {
      return new Exact(term);
    }
  }

  /**
   * {@link Contains}.
   *
   * @param term the term the value must contain
   */
  record ContainsDto(@JsonProperty(required = true) String term) implements IgnoringCaseDto {

    /* see superclass */
    @Override
    public Contains toValueMatch() {
      return new Contains(term);
    }
  }

  /**
   * {@link StartsWith}.
   *
   * @param term the term the value must start with
   */
  record StartsWithDto(@JsonProperty(required = true) String term) implements IgnoringCaseDto {

    /* see superclass */
    @Override
    public StartsWith toValueMatch() {
      return new StartsWith(term);
    }
  }

  /**
   * {@link Fuzzy}.
   *
   * @param term the term the value must approximately equal
   * @param fuzziness the allowed edit distance, e.g. "AUTO" or "1", or null for "AUTO"
   */
  record FuzzyDto(@JsonProperty(required = true) String term, String fuzziness)
      implements ValueMatchDto {

    /* see superclass */
    @Override
    public Fuzzy toValueMatch() {
      final boolean hasFuzziness = fuzziness != null;
      return hasFuzziness ? new Fuzzy(term, Fuzziness.build(fuzziness)) : new Fuzzy(term);
    }
  }

  /**
   * {@link SearchAsYouType}.
   *
   * @param term the text typed so far
   */
  record SearchAsYouTypeDto(@JsonProperty(required = true) String term) implements ValueMatchDto {

    /* see superclass */
    @Override
    public SearchAsYouType toValueMatch() {
      return new SearchAsYouType(term);
    }
  }

  /**
   * {@link OneOf}.
   *
   * @param terms the terms the value may equal
   */
  record OneOfDto(@JsonProperty(required = true) Set<String> terms) implements ValueMatchDto {

    /* see superclass */
    @Override
    public OneOf toValueMatch() {
      return new OneOf(terms);
    }
  }

  /**
   * {@link CaseInsensitive}.
   *
   * @param match the match to run ignoring case
   */
  record CaseInsensitiveDto(@JsonProperty(required = true) IgnoringCaseDto match)
      implements ValueMatchDto {

    /* see superclass */
    @Override
    public CaseInsensitive toValueMatch() {
      return new CaseInsensitive(match.toValueMatch());
    }
  }
}
