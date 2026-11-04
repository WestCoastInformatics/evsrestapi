package gov.nih.nci.evs.api.query.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import gov.nih.nci.evs.api.query.AllQualifiers;
import gov.nih.nci.evs.api.query.AnyQualifier;
import gov.nih.nci.evs.api.query.NoQualifier;
import gov.nih.nci.evs.api.query.PropertyQualifier;
import gov.nih.nci.evs.api.query.QualifierCondition;
import java.util.List;

/**
 * The JSON form of a {@link QualifierCondition}: one record per condition type, picked by the
 * {@code type} property like {@link QueryDto}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = QueryJson.TYPE_PROPERTY)
@JsonSubTypes({
  @JsonSubTypes.Type(value = QualifierConditionDto.QualifierDto.class, name = "qualifier"),
  @JsonSubTypes.Type(value = QualifierConditionDto.AllQualifiersDto.class, name = "allQualifiers"),
  @JsonSubTypes.Type(value = QualifierConditionDto.AnyQualifierDto.class, name = "anyQualifier"),
  @JsonSubTypes.Type(value = QualifierConditionDto.NoQualifierDto.class, name = "noQualifier")
})
public sealed interface QualifierConditionDto {

  /**
   * Builds the condition.
   *
   * @return the condition
   */
  QualifierCondition toCondition();

  /**
   * Builds the condition of an optional record.
   *
   * @param condition the record, or null
   * @return the condition, or null
   */
  static QualifierCondition toConditionOrNull(final QualifierConditionDto condition) {
    final boolean hasCondition = condition != null;
    return hasCondition ? condition.toCondition() : null;
  }

  /**
   * Builds the conditions of a list of records.
   *
   * @param conditions the records
   * @return the conditions
   */
  private static List<QualifierCondition> toConditions(
      final List<QualifierConditionDto> conditions) {
    return conditions.stream().map(QualifierConditionDto::toCondition).toList();
  }

  /**
   * {@link PropertyQualifier}.
   *
   * @param qualifierType how the qualifier's type name or code must match
   * @param value how the qualifier's value must match, or null for any value
   */
  record QualifierDto(
      @JsonProperty(required = true) ValueMatchDto qualifierType, ValueMatchDto value)
      implements QualifierConditionDto {

    /* see superclass */
    @Override
    public QualifierCondition toCondition() {
      final boolean hasValue = value != null;
      return hasValue
          ? new PropertyQualifier(qualifierType.toValueMatch(), value.toValueMatch())
          : new PropertyQualifier(qualifierType.toValueMatch());
    }
  }

  /**
   * {@link AllQualifiers}.
   *
   * @param conditions the conditions that must all hold
   */
  record AllQualifiersDto(@JsonProperty(required = true) List<QualifierConditionDto> conditions)
      implements QualifierConditionDto {

    /* see superclass */
    @Override
    public QualifierCondition toCondition() {
      return new AllQualifiers(toConditions(conditions));
    }
  }

  /**
   * {@link AnyQualifier}.
   *
   * @param conditions the alternative conditions
   */
  record AnyQualifierDto(@JsonProperty(required = true) List<QualifierConditionDto> conditions)
      implements QualifierConditionDto {

    /* see superclass */
    @Override
    public QualifierCondition toCondition() {
      return new AnyQualifier(toConditions(conditions));
    }
  }

  /**
   * {@link NoQualifier}.
   *
   * @param condition the condition the entry must not meet
   */
  record NoQualifierDto(@JsonProperty(required = true) QualifierConditionDto condition)
      implements QualifierConditionDto {

    /* see superclass */
    @Override
    public QualifierCondition toCondition() {
      return new NoQualifier(condition.toCondition());
    }
  }
}
