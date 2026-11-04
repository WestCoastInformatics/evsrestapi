package gov.nih.nci.evs.api.query.json;

import gov.nih.nci.evs.api.query.AllQualifiers;
import gov.nih.nci.evs.api.query.AncestorOf;
import gov.nih.nci.evs.api.query.AncestorOrSelfOf;
import gov.nih.nci.evs.api.query.And;
import gov.nih.nci.evs.api.query.AnyQualifier;
import gov.nih.nci.evs.api.query.Boost;
import gov.nih.nci.evs.api.query.CaseInsensitive;
import gov.nih.nci.evs.api.query.Contains;
import gov.nih.nci.evs.api.query.DescendantOf;
import gov.nih.nci.evs.api.query.DescendantOrSelfOf;
import gov.nih.nci.evs.api.query.EvsQuery;
import gov.nih.nci.evs.api.query.Exact;
import gov.nih.nci.evs.api.query.Fuzzy;
import gov.nih.nci.evs.api.query.HasAssociation;
import gov.nih.nci.evs.api.query.HasInverseAssociation;
import gov.nih.nci.evs.api.query.HasInverseRole;
import gov.nih.nci.evs.api.query.HasProperty;
import gov.nih.nci.evs.api.query.HasRelationship;
import gov.nih.nci.evs.api.query.HasRole;
import gov.nih.nci.evs.api.query.HasSynonym;
import gov.nih.nci.evs.api.query.HierarchyQuery;
import gov.nih.nci.evs.api.query.InSubset;
import gov.nih.nci.evs.api.query.MatchAnyOf;
import gov.nih.nci.evs.api.query.NoQualifier;
import gov.nih.nci.evs.api.query.Not;
import gov.nih.nci.evs.api.query.OneOf;
import gov.nih.nci.evs.api.query.Or;
import gov.nih.nci.evs.api.query.PropertyQualifier;
import gov.nih.nci.evs.api.query.QualifierCondition;
import gov.nih.nci.evs.api.query.RelationshipTarget;
import gov.nih.nci.evs.api.query.SearchAsYouType;
import gov.nih.nci.evs.api.query.StartsWith;
import gov.nih.nci.evs.api.query.SupportsIgnoringCase;
import gov.nih.nci.evs.api.query.Typeahead;
import gov.nih.nci.evs.api.query.ValueMatch;
import gov.nih.nci.evs.api.query.json.QualifierConditionDto.AllQualifiersDto;
import gov.nih.nci.evs.api.query.json.QualifierConditionDto.AnyQualifierDto;
import gov.nih.nci.evs.api.query.json.QualifierConditionDto.NoQualifierDto;
import gov.nih.nci.evs.api.query.json.QualifierConditionDto.QualifierDto;
import gov.nih.nci.evs.api.query.json.QueryDto.AncestorOfDto;
import gov.nih.nci.evs.api.query.json.QueryDto.AncestorOrSelfOfDto;
import gov.nih.nci.evs.api.query.json.QueryDto.AndDto;
import gov.nih.nci.evs.api.query.json.QueryDto.BoostDto;
import gov.nih.nci.evs.api.query.json.QueryDto.DescendantOfDto;
import gov.nih.nci.evs.api.query.json.QueryDto.DescendantOrSelfOfDto;
import gov.nih.nci.evs.api.query.json.QueryDto.HasAssociationDto;
import gov.nih.nci.evs.api.query.json.QueryDto.HasInverseAssociationDto;
import gov.nih.nci.evs.api.query.json.QueryDto.HasInverseRoleDto;
import gov.nih.nci.evs.api.query.json.QueryDto.HasPropertyDto;
import gov.nih.nci.evs.api.query.json.QueryDto.HasRoleDto;
import gov.nih.nci.evs.api.query.json.QueryDto.HasSynonymDto;
import gov.nih.nci.evs.api.query.json.QueryDto.InSubsetDto;
import gov.nih.nci.evs.api.query.json.QueryDto.MatchAnyOfDto;
import gov.nih.nci.evs.api.query.json.QueryDto.NotDto;
import gov.nih.nci.evs.api.query.json.QueryDto.OrDto;
import gov.nih.nci.evs.api.query.json.QueryDto.TypeaheadDto;
import gov.nih.nci.evs.api.query.json.ValueMatchDto.CaseInsensitiveDto;
import gov.nih.nci.evs.api.query.json.ValueMatchDto.ContainsDto;
import gov.nih.nci.evs.api.query.json.ValueMatchDto.ExactDto;
import gov.nih.nci.evs.api.query.json.ValueMatchDto.FuzzyDto;
import gov.nih.nci.evs.api.query.json.ValueMatchDto.IgnoringCaseDto;
import gov.nih.nci.evs.api.query.json.ValueMatchDto.OneOfDto;
import gov.nih.nci.evs.api.query.json.ValueMatchDto.SearchAsYouTypeDto;
import gov.nih.nci.evs.api.query.json.ValueMatchDto.StartsWithDto;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Builds the JSON records ({@link QueryDto} and the rest) from query nodes, the reverse of their
 * {@code to...} methods. Each node class maps to the function building its record, looked up by the
 * node's exact class, so the nodes themselves know nothing about JSON.
 *
 * <p>A node class missing here has no JSON form yet: add it here and to its record's interface.
 */
public final class QueryDtos {

  /** The record builder of each node class. */
  private static final Map<Class<?>, Function<EvsQuery, QueryDto>> QUERY_DTO_BUILDERS =
      Map.ofEntries(
          getQueryEntry(And.class, and -> new AndDto(getQueryDtos(and.getChildren()))),
          getQueryEntry(Or.class, or -> new OrDto(getQueryDtos(or.getChildren()))),
          getQueryEntry(Not.class, not -> new NotDto(getQueryDto(not.getChild()))),
          getQueryEntry(
              Boost.class, boost -> new BoostDto(getQueryDto(boost.getChild()), boost.getScore())),
          getQueryEntry(
              DescendantOf.class,
              node ->
                  new DescendantOfDto(
                      node.getAnchorCode(), getAnchorDto(node), node.getMaxLevel())),
          getQueryEntry(
              DescendantOrSelfOf.class,
              node ->
                  new DescendantOrSelfOfDto(
                      node.getAnchorCode(), getAnchorDto(node), node.getMaxLevel())),
          getQueryEntry(
              AncestorOf.class,
              node ->
                  new AncestorOfDto(node.getAnchorCode(), getAnchorDto(node), node.getMaxLevel())),
          getQueryEntry(
              AncestorOrSelfOf.class,
              node ->
                  new AncestorOrSelfOfDto(
                      node.getAnchorCode(), getAnchorDto(node), node.getMaxLevel())),
          getQueryEntry(
              HasRole.class, node -> new HasRoleDto(getTypeDto(node), getTargetDto(node))),
          getQueryEntry(
              HasInverseRole.class,
              node -> new HasInverseRoleDto(getTypeDto(node), getTargetDto(node))),
          getQueryEntry(
              HasAssociation.class,
              node -> new HasAssociationDto(getTypeDto(node), getTargetDto(node))),
          getQueryEntry(
              HasInverseAssociation.class,
              node -> new HasInverseAssociationDto(getTypeDto(node), getTargetDto(node))),
          getQueryEntry(
              HasProperty.class,
              node ->
                  new HasPropertyDto(
                      getValueMatchDto(node.getTypeNameOrCodeMatch()),
                      getValueMatchDtoOrNull(node.getValueMatch()),
                      getConditionDtoOrNull(node.getQualifierCondition()))),
          getQueryEntry(
              HasSynonym.class,
              node ->
                  new HasSynonymDto(
                      getValueMatchDtoOrNull(node.getNameMatch()),
                      getValueMatchDtoOrNull(node.getSourceMatch()),
                      getValueMatchDtoOrNull(node.getTermTypeMatch()),
                      getValueMatchDtoOrNull(node.getSubSourceMatch()))),
          getQueryEntry(
              InSubset.class, node -> new InSubsetDto(getTargetDtoOrNull(node.getSubsets()))),
          getQueryEntry(
              MatchAnyOf.class, node -> new MatchAnyOfDto(node.getField(), node.getValues())),
          getQueryEntry(Typeahead.class, node -> new TypeaheadDto(node.getIncompleteTerm())));

  /** The record builder of each match class that can ignore case. */
  private static final Map<Class<?>, Function<ValueMatch, IgnoringCaseDto>>
      IGNORING_CASE_DTO_BUILDERS =
          Map.ofEntries(
              getMatchEntry(Exact.class, match -> new ExactDto(match.term())),
              getMatchEntry(Contains.class, match -> new ContainsDto(match.term())),
              getMatchEntry(StartsWith.class, match -> new StartsWithDto(match.term())));

  /** The record builder of each other match class. */
  private static final Map<Class<?>, Function<ValueMatch, ValueMatchDto>> VALUE_MATCH_DTO_BUILDERS =
      Map.ofEntries(
          getMatchEntry(
              Fuzzy.class, match -> new FuzzyDto(match.term(), match.fuzziness().asString())),
          getMatchEntry(SearchAsYouType.class, match -> new SearchAsYouTypeDto(match.term())),
          // Sorted, so the same set always writes the same JSON
          getMatchEntry(OneOf.class, match -> new OneOfDto(new TreeSet<>(match.terms()))),
          getMatchEntry(
              CaseInsensitive.class,
              match -> new CaseInsensitiveDto(getIgnoringCaseDto(match.match()))));

  /** The record builder of each qualifier condition class. */
  private static final Map<Class<?>, Function<QualifierCondition, QualifierConditionDto>>
      CONDITION_DTO_BUILDERS =
          Map.ofEntries(
              getConditionEntry(
                  PropertyQualifier.class,
                  qualifier ->
                      new QualifierDto(
                          getValueMatchDto(qualifier.getTypeNameOrCodeMatch()),
                          getValueMatchDtoOrNull(qualifier.getValueMatch()))),
              getConditionEntry(
                  AllQualifiers.class,
                  all -> new AllQualifiersDto(getConditionDtos(all.getConditions()))),
              getConditionEntry(
                  AnyQualifier.class,
                  any -> new AnyQualifierDto(getConditionDtos(any.getConditions()))),
              getConditionEntry(
                  NoQualifier.class,
                  none -> new NoQualifierDto(getConditionDto(none.getCondition()))));

  /** Only static helpers. */
  private QueryDtos() {}

  /**
   * Returns the record of a query node.
   *
   * @param query the node
   * @return the record
   * @throws IllegalArgumentException if the node's class has no JSON form
   */
  public static QueryDto getQueryDto(final EvsQuery query) {
    return getBuilder(QUERY_DTO_BUILDERS, query).apply(query);
  }

  /**
   * Returns the records of a list of query nodes.
   *
   * @param queries the nodes
   * @return the records
   */
  private static List<QueryDto> getQueryDtos(final List<EvsQuery> queries) {
    return queries.stream().map(QueryDtos::getQueryDto).toList();
  }

  /**
   * Returns the anchor query's record of a hierarchy node built from one, or null for a node built
   * from a code, whose code is written instead.
   *
   * @param node the hierarchy node
   * @return the anchor record, or null
   */
  private static QueryDto getAnchorDto(final HierarchyQuery node) {
    final boolean hasAnchorCode = node.getAnchorCode() != null;
    return hasAnchorCode ? null : getQueryDto(node.getAnchorQuery());
  }

  /**
   * Returns the record of a relationship node's type, which is just its match.
   *
   * @param node the relationship node
   * @return the type's match record
   */
  private static ValueMatchDto getTypeDto(final HasRelationship node) {
    return getValueMatchDto(node.getRelationshipType().getNameOrCodeMatch());
  }

  /**
   * Returns the record of a relationship node's target.
   *
   * @param node the relationship node
   * @return the target record, or null for any target
   */
  private static RelationshipTargetDto getTargetDto(final HasRelationship node) {
    return getTargetDtoOrNull(node.getRelationshipTarget());
  }

  /**
   * Returns the record of an optional relationship target.
   *
   * @param target the target, or null
   * @return the target record, or null
   */
  private static RelationshipTargetDto getTargetDtoOrNull(final RelationshipTarget target) {
    final boolean hasTarget = target != null;
    if (!hasTarget) {
      return null;
    }

    final boolean hasQuery = target.getQuery() != null;
    return hasQuery
        ? new RelationshipTargetDto(getQueryDto(target.getQuery()), null)
        : new RelationshipTargetDto(null, getValueMatchDto(target.getNameOrCodeMatch()));
  }

  /**
   * Returns the record of a value match.
   *
   * @param match the match
   * @return the record
   * @throws IllegalArgumentException if the match's class has no JSON form
   */
  private static ValueMatchDto getValueMatchDto(final ValueMatch match) {
    final boolean canIgnoreCase = IGNORING_CASE_DTO_BUILDERS.containsKey(match.getClass());
    return canIgnoreCase
        ? IGNORING_CASE_DTO_BUILDERS.get(match.getClass()).apply(match)
        : getBuilder(VALUE_MATCH_DTO_BUILDERS, match).apply(match);
  }

  /**
   * Returns the record of an optional value match.
   *
   * @param match the match, or null
   * @return the record, or null
   */
  private static ValueMatchDto getValueMatchDtoOrNull(final ValueMatch match) {
    final boolean hasMatch = match != null;
    return hasMatch ? getValueMatchDto(match) : null;
  }

  /**
   * Returns the record of a match that can ignore case.
   *
   * @param match the match
   * @return the record
   * @throws IllegalArgumentException if the match's class has no JSON form
   */
  private static IgnoringCaseDto getIgnoringCaseDto(final SupportsIgnoringCase match) {
    return getBuilder(IGNORING_CASE_DTO_BUILDERS, match).apply(match);
  }

  /**
   * Returns the record of a qualifier condition.
   *
   * @param condition the condition
   * @return the record
   * @throws IllegalArgumentException if the condition's class has no JSON form
   */
  private static QualifierConditionDto getConditionDto(final QualifierCondition condition) {
    return getBuilder(CONDITION_DTO_BUILDERS, condition).apply(condition);
  }

  /**
   * Returns the record of an optional qualifier condition.
   *
   * @param condition the condition, or null
   * @return the record, or null
   */
  private static QualifierConditionDto getConditionDtoOrNull(final QualifierCondition condition) {
    final boolean hasCondition = condition != null;
    return hasCondition ? getConditionDto(condition) : null;
  }

  /**
   * Returns the records of a list of qualifier conditions.
   *
   * @param conditions the conditions
   * @return the records
   */
  private static List<QualifierConditionDto> getConditionDtos(
      final List<QualifierCondition> conditions) {
    return conditions.stream().map(QueryDtos::getConditionDto).toList();
  }

  /**
   * Returns the record builder for an object's exact class.
   *
   * @param builders the record builder of each class
   * @param value the object
   * @return the builder
   * @throws IllegalArgumentException if the class has no builder
   */
  private static <T, D> Function<T, D> getBuilder(
      final Map<Class<?>, Function<T, D>> builders, final T value) {
    final Function<T, D> builder = builders.get(value.getClass());
    final boolean hasJsonForm = builder != null;
    if (!hasJsonForm) {
      throw new IllegalArgumentException(
          "No JSON form for " + value.getClass().getSimpleName() + " yet");
    }

    return builder;
  }

  /**
   * Returns a map entry from a node class to its record builder, which may use the class's own
   * getters.
   *
   * @param type the node class
   * @param builder the record builder
   * @return the entry
   */
  private static <T extends EvsQuery>
      Map.Entry<Class<?>, Function<EvsQuery, QueryDto>> getQueryEntry(
          final Class<T> type, final Function<T, QueryDto> builder) {
    return Map.entry(type, query -> builder.apply(type.cast(query)));
  }

  /**
   * Returns a map entry from a match class to its record builder, which may use the class's own
   * accessors.
   *
   * @param type the match class
   * @param builder the record builder
   * @return the entry
   */
  private static <T extends ValueMatch, D extends ValueMatchDto>
      Map.Entry<Class<?>, Function<ValueMatch, D>> getMatchEntry(
          final Class<T> type, final Function<T, D> builder) {
    return Map.entry(type, match -> builder.apply(type.cast(match)));
  }

  /**
   * Returns a map entry from a qualifier condition class to its record builder, which may use the
   * class's own getters.
   *
   * @param type the condition class
   * @param builder the record builder
   * @return the entry
   */
  private static <T extends QualifierCondition>
      Map.Entry<Class<?>, Function<QualifierCondition, QualifierConditionDto>> getConditionEntry(
          final Class<T> type, final Function<T, QualifierConditionDto> builder) {
    return Map.entry(type, condition -> builder.apply(type.cast(condition)));
  }
}
