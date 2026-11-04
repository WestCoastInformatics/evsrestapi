package gov.nih.nci.evs.api.query.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import gov.nih.nci.evs.api.model.Terminology;
import gov.nih.nci.evs.api.query.AncestorOf;
import gov.nih.nci.evs.api.query.AncestorOrSelfOf;
import gov.nih.nci.evs.api.query.And;
import gov.nih.nci.evs.api.query.Boost;
import gov.nih.nci.evs.api.query.DescendantOf;
import gov.nih.nci.evs.api.query.DescendantOrSelfOf;
import gov.nih.nci.evs.api.query.EvsQuery;
import gov.nih.nci.evs.api.query.HasAssociation;
import gov.nih.nci.evs.api.query.HasInverseAssociation;
import gov.nih.nci.evs.api.query.HasInverseRole;
import gov.nih.nci.evs.api.query.HasProperty;
import gov.nih.nci.evs.api.query.HasRole;
import gov.nih.nci.evs.api.query.HasSynonym;
import gov.nih.nci.evs.api.query.InSubset;
import gov.nih.nci.evs.api.query.MatchAnyOf;
import gov.nih.nci.evs.api.query.Not;
import gov.nih.nci.evs.api.query.Or;
import gov.nih.nci.evs.api.query.RelationshipType;
import gov.nih.nci.evs.api.query.Typeahead;
import java.util.List;
import java.util.Set;

/**
 * The JSON form of a query node: one record per node type, holding what the node is built from.
 * Jackson picks the record from the {@code type} property, using the names below, and the record
 * builds the node with {@link #toQuery}. {@link QueryDtos} goes the other way.
 *
 * <p>The records never hold terminologies: the request gives them (see {@link QueryJson}).
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = QueryJson.TYPE_PROPERTY)
@JsonSubTypes({
  @JsonSubTypes.Type(value = QueryDto.AndDto.class, name = "and"),
  @JsonSubTypes.Type(value = QueryDto.OrDto.class, name = "or"),
  @JsonSubTypes.Type(value = QueryDto.NotDto.class, name = "not"),
  @JsonSubTypes.Type(value = QueryDto.BoostDto.class, name = "boost"),
  @JsonSubTypes.Type(value = QueryDto.DescendantOfDto.class, name = "descendantOf"),
  @JsonSubTypes.Type(value = QueryDto.DescendantOrSelfOfDto.class, name = "descendantOrSelfOf"),
  @JsonSubTypes.Type(value = QueryDto.AncestorOfDto.class, name = "ancestorOf"),
  @JsonSubTypes.Type(value = QueryDto.AncestorOrSelfOfDto.class, name = "ancestorOrSelfOf"),
  @JsonSubTypes.Type(value = QueryDto.HasRoleDto.class, name = "hasRole"),
  @JsonSubTypes.Type(value = QueryDto.HasInverseRoleDto.class, name = "hasInverseRole"),
  @JsonSubTypes.Type(value = QueryDto.HasAssociationDto.class, name = "hasAssociation"),
  @JsonSubTypes.Type(
      value = QueryDto.HasInverseAssociationDto.class,
      name = "hasInverseAssociation"),
  @JsonSubTypes.Type(value = QueryDto.HasPropertyDto.class, name = "hasProperty"),
  @JsonSubTypes.Type(value = QueryDto.HasSynonymDto.class, name = "hasSynonym"),
  @JsonSubTypes.Type(value = QueryDto.InSubsetDto.class, name = "inSubset"),
  @JsonSubTypes.Type(value = QueryDto.MatchAnyOfDto.class, name = "matchAnyOf"),
  @JsonSubTypes.Type(value = QueryDto.TypeaheadDto.class, name = "typeahead")
})
public sealed interface QueryDto {

  /**
   * Builds the query node.
   *
   * @param terminologies the terminologies being searched, from the request
   * @return the node
   */
  EvsQuery toQuery(List<Terminology> terminologies);

  /**
   * Builds the nodes of a list of records.
   *
   * @param queries the records
   * @param terminologies the terminologies being searched
   * @return the nodes
   */
  private static List<EvsQuery> toQueries(
      final List<QueryDto> queries, final List<Terminology> terminologies) {
    return queries.stream().map(query -> query.toQuery(terminologies)).toList();
  }

  /**
   * Checks that a hierarchy node has exactly one anchor: a code or a nested query.
   *
   * @param code the anchor code, or null
   * @param anchor the anchor query, or null
   * @throws IllegalArgumentException if both or neither are given
   */
  private static void checkOneAnchor(final String code, final QueryDto anchor) {
    final boolean hasOneAnchor = (code == null) != (anchor == null);
    if (!hasOneAnchor) {
      throw new IllegalArgumentException(
          "A hierarchy node needs exactly one of 'code' or 'anchor'");
    }
  }

  /**
   * {@link And}.
   *
   * @param children the queries that must all match
   */
  record AndDto(@JsonProperty(required = true) List<QueryDto> children) implements QueryDto {

    /* see superclass */
    @Override
    public EvsQuery toQuery(final List<Terminology> terminologies) {
      return new And(terminologies, toQueries(children, terminologies));
    }
  }

  /**
   * {@link Or}.
   *
   * @param children the queries of which at least one must match
   */
  record OrDto(@JsonProperty(required = true) List<QueryDto> children) implements QueryDto {

    /* see superclass */
    @Override
    public EvsQuery toQuery(final List<Terminology> terminologies) {
      return new Or(terminologies, toQueries(children, terminologies));
    }
  }

  /**
   * {@link Not}.
   *
   * @param child the query concepts must not match
   */
  record NotDto(@JsonProperty(required = true) QueryDto child) implements QueryDto {

    /* see superclass */
    @Override
    public EvsQuery toQuery(final List<Terminology> terminologies) {
      return new Not(terminologies, child.toQuery(terminologies));
    }
  }

  /**
   * {@link Boost}.
   *
   * @param child the query whose matches are scored
   * @param score the score every match gets
   */
  record BoostDto(
      @JsonProperty(required = true) QueryDto child, @JsonProperty(required = true) float score)
      implements QueryDto {

    /* see superclass */
    @Override
    public EvsQuery toQuery(final List<Terminology> terminologies) {
      return new Boost(terminologies, child.toQuery(terminologies), score);
    }
  }

  /**
   * {@link DescendantOf}.
   *
   * @param code the anchor concept's code, or null for an anchor query
   * @param anchor the anchor query, or null for an anchor code
   * @param maxLevel the maximum depth below the anchors, or null for any depth
   */
  record DescendantOfDto(String code, QueryDto anchor, Integer maxLevel) implements QueryDto {

    /** Checks the anchor. */
    public DescendantOfDto {
      checkOneAnchor(code, anchor);
    }

    /* see superclass */
    @Override
    public EvsQuery toQuery(final List<Terminology> terminologies) {
      final boolean hasCode = code != null;
      final boolean hasMaxLevel = maxLevel != null;
      if (hasCode) {
        return hasMaxLevel
            ? new DescendantOf(terminologies, code, maxLevel)
            : new DescendantOf(terminologies, code);
      }

      final EvsQuery anchorQuery = anchor.toQuery(terminologies);
      return hasMaxLevel
          ? new DescendantOf(terminologies, anchorQuery, maxLevel)
          : new DescendantOf(terminologies, anchorQuery);
    }
  }

  /**
   * {@link DescendantOrSelfOf}.
   *
   * @param code the anchor concept's code, or null for an anchor query
   * @param anchor the anchor query, or null for an anchor code
   * @param maxLevel the maximum depth below the anchors, or null for any depth
   */
  record DescendantOrSelfOfDto(String code, QueryDto anchor, Integer maxLevel) implements QueryDto {

    /** Checks the anchor. */
    public DescendantOrSelfOfDto {
      checkOneAnchor(code, anchor);
    }

    /* see superclass */
    @Override
    public EvsQuery toQuery(final List<Terminology> terminologies) {
      final boolean hasCode = code != null;
      final boolean hasMaxLevel = maxLevel != null;
      if (hasCode) {
        return hasMaxLevel
            ? new DescendantOrSelfOf(terminologies, code, maxLevel)
            : new DescendantOrSelfOf(terminologies, code);
      }

      final EvsQuery anchorQuery = anchor.toQuery(terminologies);
      return hasMaxLevel
          ? new DescendantOrSelfOf(terminologies, anchorQuery, maxLevel)
          : new DescendantOrSelfOf(terminologies, anchorQuery);
    }
  }

  /**
   * {@link AncestorOf}.
   *
   * @param code the anchor concept's code, or null for an anchor query
   * @param anchor the anchor query, or null for an anchor code
   * @param maxLevel the maximum distance above the anchors, or null for any distance
   */
  record AncestorOfDto(String code, QueryDto anchor, Integer maxLevel) implements QueryDto {

    /** Checks the anchor. */
    public AncestorOfDto {
      checkOneAnchor(code, anchor);
    }

    /* see superclass */
    @Override
    public EvsQuery toQuery(final List<Terminology> terminologies) {
      final boolean hasCode = code != null;
      final boolean hasMaxLevel = maxLevel != null;
      if (hasCode) {
        return hasMaxLevel
            ? new AncestorOf(terminologies, code, maxLevel)
            : new AncestorOf(terminologies, code);
      }

      final EvsQuery anchorQuery = anchor.toQuery(terminologies);
      return hasMaxLevel
          ? new AncestorOf(terminologies, anchorQuery, maxLevel)
          : new AncestorOf(terminologies, anchorQuery);
    }
  }

  /**
   * {@link AncestorOrSelfOf}.
   *
   * @param code the anchor concept's code, or null for an anchor query
   * @param anchor the anchor query, or null for an anchor code
   * @param maxLevel the maximum distance above the anchors, or null for any distance
   */
  record AncestorOrSelfOfDto(String code, QueryDto anchor, Integer maxLevel) implements QueryDto {

    /** Checks the anchor. */
    public AncestorOrSelfOfDto {
      checkOneAnchor(code, anchor);
    }

    /* see superclass */
    @Override
    public EvsQuery toQuery(final List<Terminology> terminologies) {
      final boolean hasCode = code != null;
      final boolean hasMaxLevel = maxLevel != null;
      if (hasCode) {
        return hasMaxLevel
            ? new AncestorOrSelfOf(terminologies, code, maxLevel)
            : new AncestorOrSelfOf(terminologies, code);
      }

      final EvsQuery anchorQuery = anchor.toQuery(terminologies);
      return hasMaxLevel
          ? new AncestorOrSelfOf(terminologies, anchorQuery, maxLevel)
          : new AncestorOrSelfOf(terminologies, anchorQuery);
    }
  }

  /**
   * {@link HasRole}.
   *
   * @param relationshipType how the role's type name or code must match
   * @param target what the role must point to, or null for any target
   */
  record HasRoleDto(
      @JsonProperty(required = true) ValueMatchDto relationshipType, RelationshipTargetDto target)
      implements QueryDto {

    /* see superclass */
    @Override
    public EvsQuery toQuery(final List<Terminology> terminologies) {
      final boolean hasTarget = target != null;
      final RelationshipType type = RelationshipTargetDto.toRelationshipType(relationshipType);
      return hasTarget
          ? new HasRole(terminologies, type, target.toTarget(terminologies))
          : new HasRole(terminologies, type);
    }
  }

  /**
   * {@link HasInverseRole}.
   *
   * @param relationshipType how the role's type name or code must match
   * @param target the concepts holding the role, or null for any
   */
  record HasInverseRoleDto(
      @JsonProperty(required = true) ValueMatchDto relationshipType, RelationshipTargetDto target)
      implements QueryDto {

    /* see superclass */
    @Override
    public EvsQuery toQuery(final List<Terminology> terminologies) {
      final boolean hasTarget = target != null;
      final RelationshipType type = RelationshipTargetDto.toRelationshipType(relationshipType);
      return hasTarget
          ? new HasInverseRole(terminologies, type, target.toTarget(terminologies))
          : new HasInverseRole(terminologies, type);
    }
  }

  /**
   * {@link HasAssociation}.
   *
   * @param relationshipType how the association's type name or code must match
   * @param target what the association must point to, or null for any target
   */
  record HasAssociationDto(
      @JsonProperty(required = true) ValueMatchDto relationshipType, RelationshipTargetDto target)
      implements QueryDto {

    /* see superclass */
    @Override
    public EvsQuery toQuery(final List<Terminology> terminologies) {
      final boolean hasTarget = target != null;
      final RelationshipType type = RelationshipTargetDto.toRelationshipType(relationshipType);
      return hasTarget
          ? new HasAssociation(terminologies, type, target.toTarget(terminologies))
          : new HasAssociation(terminologies, type);
    }
  }

  /**
   * {@link HasInverseAssociation}.
   *
   * @param relationshipType how the association's type name or code must match
   * @param target the concepts holding the association, or null for any
   */
  record HasInverseAssociationDto(
      @JsonProperty(required = true) ValueMatchDto relationshipType, RelationshipTargetDto target)
      implements QueryDto {

    /* see superclass */
    @Override
    public EvsQuery toQuery(final List<Terminology> terminologies) {
      final boolean hasTarget = target != null;
      final RelationshipType type = RelationshipTargetDto.toRelationshipType(relationshipType);
      return hasTarget
          ? new HasInverseAssociation(terminologies, type, target.toTarget(terminologies))
          : new HasInverseAssociation(terminologies, type);
    }
  }

  /**
   * {@link HasProperty}.
   *
   * @param propertyType how the property's type name or code must match
   * @param value how the property's value must match, or null for any value
   * @param qualifiers the condition the property's qualifiers must meet, or null for none
   */
  record HasPropertyDto(
      @JsonProperty(required = true) ValueMatchDto propertyType,
      ValueMatchDto value,
      QualifierConditionDto qualifiers)
      implements QueryDto {

    /* see superclass */
    @Override
    public EvsQuery toQuery(final List<Terminology> terminologies) {
      return new HasProperty(
          terminologies,
          propertyType.toValueMatch(),
          ValueMatchDto.toValueMatchOrNull(value),
          QualifierConditionDto.toConditionOrNull(qualifiers));
    }
  }

  /**
   * {@link HasSynonym}.
   *
   * @param name how the synonym's name must match, or null for any name
   * @param source how the synonym's source must match, or null for any source
   * @param termType how the synonym's term type must match, or null for any term type
   * @param subSource how the synonym's sub-source must match, or null for any sub-source
   */
  record HasSynonymDto(
      ValueMatchDto name, ValueMatchDto source, ValueMatchDto termType, ValueMatchDto subSource)
      implements QueryDto {

    /* see superclass */
    @Override
    public EvsQuery toQuery(final List<Terminology> terminologies) {
      return new HasSynonym(
          terminologies,
          ValueMatchDto.toValueMatchOrNull(name),
          ValueMatchDto.toValueMatchOrNull(source),
          ValueMatchDto.toValueMatchOrNull(termType),
          ValueMatchDto.toValueMatchOrNull(subSource));
    }
  }

  /**
   * {@link InSubset}.
   *
   * @param subsets the subsets, as a match on their code or name or as a query over them
   */
  record InSubsetDto(@JsonProperty(required = true) RelationshipTargetDto subsets)
      implements QueryDto {

    /* see superclass */
    @Override
    public EvsQuery toQuery(final List<Terminology> terminologies) {
      return new InSubset(terminologies, subsets.toTarget(terminologies));
    }
  }

  /**
   * {@link MatchAnyOf}.
   *
   * @param field the field to match against
   * @param values the values, any of which the field may equal
   */
  record MatchAnyOfDto(
      @JsonProperty(required = true) String field,
      @JsonProperty(required = true) Set<String> values)
      implements QueryDto {

    /* see superclass */
    @Override
    public EvsQuery toQuery(final List<Terminology> terminologies) {
      return new MatchAnyOf(terminologies, field, values);
    }
  }

  /**
   * {@link Typeahead}.
   *
   * @param term the text typed so far
   */
  record TypeaheadDto(@JsonProperty(required = true) String term) implements QueryDto {

    /* see superclass */
    @Override
    public EvsQuery toQuery(final List<Terminology> terminologies) {
      return new Typeahead(terminologies, term);
    }
  }
}
