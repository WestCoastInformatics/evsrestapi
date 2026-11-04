package gov.nih.nci.evs.api.query.json;

import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.OptBoolean;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.util.StdConverter;
import gov.nih.nci.evs.api.model.Terminology;
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
import gov.nih.nci.evs.api.query.HasRole;
import gov.nih.nci.evs.api.query.HasSynonym;
import gov.nih.nci.evs.api.query.InSubset;
import gov.nih.nci.evs.api.query.MatchAnyOf;
import gov.nih.nci.evs.api.query.NoQualifier;
import gov.nih.nci.evs.api.query.Not;
import gov.nih.nci.evs.api.query.OneOf;
import gov.nih.nci.evs.api.query.Or;
import gov.nih.nci.evs.api.query.PropertyQualifier;
import gov.nih.nci.evs.api.query.QualifierCondition;
import gov.nih.nci.evs.api.query.RelationshipTarget;
import gov.nih.nci.evs.api.query.RelationshipType;
import gov.nih.nci.evs.api.query.SearchAsYouType;
import gov.nih.nci.evs.api.query.StartsWith;
import gov.nih.nci.evs.api.query.Typeahead;
import gov.nih.nci.evs.api.query.ValueMatch;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

/**
 * Jackson mix-ins for the query classes: annotations that {@link QueryJson} attaches to each class
 * from the outside, so the query classes themselves carry no Jackson annotations. A mix-in's
 * methods and constructors stand for the ones of the same signature on its class.
 *
 * <p>Getters marked {@link JsonProperty} are written. Constructors marked {@link JsonCreator} build
 * nodes on reading, with the request's terminologies injected ({@link JacksonInject}). Nodes whose
 * JSON doesn't match one constructor are read through a builder ({@link JsonBuilders}) instead.
 */
final class QueryMixins {

  /** Only nested mix-ins. */
  private QueryMixins() {}

  /** Names each node type, and leaves the terminologies out: the request gives them. */
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = QueryJson.TYPE_PROPERTY)
  @JsonSubTypes({
    @JsonSubTypes.Type(value = And.class, name = "and"),
    @JsonSubTypes.Type(value = Or.class, name = "or"),
    @JsonSubTypes.Type(value = Not.class, name = "not"),
    @JsonSubTypes.Type(value = Boost.class, name = "boost"),
    @JsonSubTypes.Type(value = DescendantOf.class, name = "descendantOf"),
    @JsonSubTypes.Type(value = DescendantOrSelfOf.class, name = "descendantOrSelfOf"),
    @JsonSubTypes.Type(value = AncestorOf.class, name = "ancestorOf"),
    @JsonSubTypes.Type(value = AncestorOrSelfOf.class, name = "ancestorOrSelfOf"),
    @JsonSubTypes.Type(value = HasRole.class, name = "hasRole"),
    @JsonSubTypes.Type(value = HasInverseRole.class, name = "hasInverseRole"),
    @JsonSubTypes.Type(value = HasAssociation.class, name = "hasAssociation"),
    @JsonSubTypes.Type(value = HasInverseAssociation.class, name = "hasInverseAssociation"),
    @JsonSubTypes.Type(value = HasProperty.class, name = "hasProperty"),
    @JsonSubTypes.Type(value = HasSynonym.class, name = "hasSynonym"),
    @JsonSubTypes.Type(value = InSubset.class, name = "inSubset"),
    @JsonSubTypes.Type(value = MatchAnyOf.class, name = "matchAnyOf"),
    @JsonSubTypes.Type(value = Typeahead.class, name = "typeahead")
  })
  abstract static class EvsQueryMixin {

    /**
     * Not written: the request gives the terminologies.
     *
     * @return the terminologies
     */
    @JsonIgnore
    abstract List<Terminology> getTerminologies();

    /**
     * Not written: internal.
     *
     * @return the index coordinates
     */
    @JsonIgnore
    abstract IndexCoordinates getIndexCoordinates();
  }

  /** {@link And}: {@code children}. */
  abstract static class AndMixin {

    /**
     * Builds the node.
     *
     * @param terminologies the request's terminologies
     * @param children the queries that must all match
     */
    @JsonCreator
    AndMixin(
        @JacksonInject(value = QueryJson.TERMINOLOGIES, useInput = OptBoolean.FALSE)
            final List<Terminology> terminologies,
        @JsonProperty(value = "children", required = true) final List<EvsQuery> children) {}

    /**
     * Written as {@code children}.
     *
     * @return the children
     */
    @JsonProperty("children")
    abstract List<EvsQuery> getChildren();
  }

  /** {@link Or}: {@code children}. */
  abstract static class OrMixin {

    /**
     * Builds the node.
     *
     * @param terminologies the request's terminologies
     * @param children the queries of which at least one must match
     */
    @JsonCreator
    OrMixin(
        @JacksonInject(value = QueryJson.TERMINOLOGIES, useInput = OptBoolean.FALSE)
            final List<Terminology> terminologies,
        @JsonProperty(value = "children", required = true) final List<EvsQuery> children) {}

    /**
     * Written as {@code children}.
     *
     * @return the children
     */
    @JsonProperty("children")
    abstract List<EvsQuery> getChildren();
  }

  /** {@link Not}: {@code child}. */
  abstract static class NotMixin {

    /**
     * Builds the node.
     *
     * @param terminologies the request's terminologies
     * @param child the query concepts must not match
     */
    @JsonCreator
    NotMixin(
        @JacksonInject(value = QueryJson.TERMINOLOGIES, useInput = OptBoolean.FALSE)
            final List<Terminology> terminologies,
        @JsonProperty(value = "child", required = true) final EvsQuery child) {}

    /**
     * Written as {@code child}.
     *
     * @return the child
     */
    @JsonProperty("child")
    abstract EvsQuery getChild();
  }

  /** {@link Boost}: {@code child} and {@code score}. */
  abstract static class BoostMixin {

    /**
     * Builds the node.
     *
     * @param terminologies the request's terminologies
     * @param child the query whose matches are scored
     * @param score the score every match gets
     */
    @JsonCreator
    BoostMixin(
        @JacksonInject(value = QueryJson.TERMINOLOGIES, useInput = OptBoolean.FALSE)
            final List<Terminology> terminologies,
        @JsonProperty(value = "child", required = true) final EvsQuery child,
        @JsonProperty(value = "score", required = true) final float score) {}

    /**
     * Written as {@code child}.
     *
     * @return the child
     */
    @JsonProperty("child")
    abstract EvsQuery getChild();

    /**
     * Written as {@code score}.
     *
     * @return the score
     */
    @JsonProperty("score")
    abstract float getScore();
  }

  /** {@link DescendantOf}: read by a builder, written by {@link HierarchyQuerySerializer}. */
  @JsonDeserialize(builder = JsonBuilders.DescendantOfBuilder.class)
  abstract static class DescendantOfMixin {}

  /** {@link DescendantOrSelfOf}: read by a builder, written by {@link HierarchyQuerySerializer}. */
  @JsonDeserialize(builder = JsonBuilders.DescendantOrSelfOfBuilder.class)
  abstract static class DescendantOrSelfOfMixin {}

  /** {@link AncestorOf}: read by a builder, written by {@link HierarchyQuerySerializer}. */
  @JsonDeserialize(builder = JsonBuilders.AncestorOfBuilder.class)
  abstract static class AncestorOfMixin {}

  /** {@link AncestorOrSelfOf}: read by a builder, written by {@link HierarchyQuerySerializer}. */
  @JsonDeserialize(builder = JsonBuilders.AncestorOrSelfOfBuilder.class)
  abstract static class AncestorOrSelfOfMixin {}

  /** Relationship nodes: {@code relationshipType} and an optional {@code target}. */
  abstract static class HasRelationshipMixin {

    /**
     * Written as {@code relationshipType}.
     *
     * @return the relationship type
     */
    @JsonProperty("relationshipType")
    abstract RelationshipType getRelationshipType();

    /**
     * Written as {@code target}, when there is one.
     *
     * @return the target, or null
     */
    @JsonProperty("target")
    abstract RelationshipTarget getRelationshipTarget();

    /**
     * Not written: {@code target} says it.
     *
     * @return whether there is a target
     */
    @JsonIgnore
    abstract boolean getHasRelationshipTarget();
  }

  /** {@link HasRole}: read by a builder, since a missing target picks another constructor. */
  @JsonDeserialize(builder = JsonBuilders.HasRoleBuilder.class)
  abstract static class HasRoleMixin {}

  /** {@link HasInverseRole}: read by a builder, like {@link HasRoleMixin}. */
  @JsonDeserialize(builder = JsonBuilders.HasInverseRoleBuilder.class)
  abstract static class HasInverseRoleMixin {}

  /** {@link HasAssociation}: read by a builder, like {@link HasRoleMixin}. */
  @JsonDeserialize(builder = JsonBuilders.HasAssociationBuilder.class)
  abstract static class HasAssociationMixin {}

  /** {@link HasInverseAssociation}: read by a builder, like {@link HasRoleMixin}. */
  @JsonDeserialize(builder = JsonBuilders.HasInverseAssociationBuilder.class)
  abstract static class HasInverseAssociationMixin {}

  /** {@link RelationshipType}: written and read as just its match. */
  abstract static class RelationshipTypeMixin {

    /**
     * Builds the type from its match.
     *
     * @param nameOrCodeMatch how the type name or code must match
     */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    RelationshipTypeMixin(final ValueMatch nameOrCodeMatch) {}

    /**
     * Written as the whole type.
     *
     * @return the match
     */
    @JsonValue
    abstract ValueMatch getNameOrCodeMatch();
  }

  /** {@link RelationshipTarget}: {@code query} or {@code match}, read by a builder. */
  @JsonDeserialize(builder = JsonBuilders.RelationshipTargetBuilder.class)
  abstract static class RelationshipTargetMixin {

    /**
     * Written as {@code query}, for a target query.
     *
     * @return the query, or null
     */
    @JsonProperty("query")
    abstract EvsQuery getQuery();

    /**
     * Written as {@code match}, for a target match.
     *
     * @return the match, or null
     */
    @JsonProperty("match")
    abstract ValueMatch getNameOrCodeMatch();
  }

  /** {@link HasProperty}: {@code propertyType}, optional {@code value} and {@code qualifiers}. */
  abstract static class HasPropertyMixin {

    /**
     * Builds the node.
     *
     * @param terminologies the request's terminologies
     * @param typeNameOrCodeMatch how the property's type name or code must match
     * @param valueMatch how the property's value must match, or null
     * @param qualifierCondition the condition the qualifiers must meet, or null
     */
    @JsonCreator
    HasPropertyMixin(
        @JacksonInject(value = QueryJson.TERMINOLOGIES, useInput = OptBoolean.FALSE)
            final List<Terminology> terminologies,
        @JsonProperty(value = "propertyType", required = true) final ValueMatch typeNameOrCodeMatch,
        @JsonProperty("value") final ValueMatch valueMatch,
        @JsonProperty("qualifiers") final QualifierCondition qualifierCondition) {}

    /**
     * Written as {@code propertyType}.
     *
     * @return the type match
     */
    @JsonProperty("propertyType")
    abstract ValueMatch getTypeNameOrCodeMatch();

    /**
     * Written as {@code value}, when there is one.
     *
     * @return the value match, or null
     */
    @JsonProperty("value")
    abstract ValueMatch getValueMatch();

    /**
     * Written as {@code qualifiers}, when there is one.
     *
     * @return the qualifier condition, or null
     */
    @JsonProperty("qualifiers")
    abstract QualifierCondition getQualifierCondition();
  }

  /** {@link HasSynonym}: optional {@code name}, {@code source}, {@code termType}, etc. */
  abstract static class HasSynonymMixin {

    /**
     * Builds the node.
     *
     * @param terminologies the request's terminologies
     * @param nameMatch how the name must match, or null
     * @param sourceMatch how the source must match, or null
     * @param termTypeMatch how the term type must match, or null
     * @param subSourceMatch how the sub-source must match, or null
     */
    @JsonCreator
    HasSynonymMixin(
        @JacksonInject(value = QueryJson.TERMINOLOGIES, useInput = OptBoolean.FALSE)
            final List<Terminology> terminologies,
        @JsonProperty("name") final ValueMatch nameMatch,
        @JsonProperty("source") final ValueMatch sourceMatch,
        @JsonProperty("termType") final ValueMatch termTypeMatch,
        @JsonProperty("subSource") final ValueMatch subSourceMatch) {}

    /**
     * Written as {@code name}, as given.
     *
     * @return the name match, or null
     */
    @JsonProperty("name")
    abstract ValueMatch getNameMatch();

    /**
     * Written as {@code source}.
     *
     * @return the source match, or null
     */
    @JsonProperty("source")
    abstract ValueMatch getSourceMatch();

    /**
     * Written as {@code termType}.
     *
     * @return the term type match, or null
     */
    @JsonProperty("termType")
    abstract ValueMatch getTermTypeMatch();

    /**
     * Written as {@code subSource}.
     *
     * @return the sub-source match, or null
     */
    @JsonProperty("subSource")
    abstract ValueMatch getSubSourceMatch();
  }

  /** {@link InSubset}: {@code subsets}. */
  abstract static class InSubsetMixin {

    /**
     * Builds the node.
     *
     * @param terminologies the request's terminologies
     * @param subsets the subsets
     */
    @JsonCreator
    InSubsetMixin(
        @JacksonInject(value = QueryJson.TERMINOLOGIES, useInput = OptBoolean.FALSE)
            final List<Terminology> terminologies,
        @JsonProperty(value = "subsets", required = true) final RelationshipTarget subsets) {}

    /**
     * Written as {@code subsets}.
     *
     * @return the subsets
     */
    @JsonProperty("subsets")
    abstract RelationshipTarget getSubsets();
  }

  /** {@link MatchAnyOf}: {@code field} and {@code values}. */
  abstract static class MatchAnyOfMixin {

    /**
     * Builds the node.
     *
     * @param terminologies the request's terminologies
     * @param field the field to match against
     * @param values the values, any of which the field may equal
     */
    @JsonCreator
    MatchAnyOfMixin(
        @JacksonInject(value = QueryJson.TERMINOLOGIES, useInput = OptBoolean.FALSE)
            final List<Terminology> terminologies,
        @JsonProperty(value = "field", required = true) final String field,
        @JsonProperty(value = "values", required = true) final Collection<String> values) {}
  }

  /** {@link Typeahead}: {@code term}. */
  abstract static class TypeaheadMixin {

    /**
     * Builds the node.
     *
     * @param terminologies the request's terminologies
     * @param incompleteTerm the text typed so far
     */
    @JsonCreator
    TypeaheadMixin(
        @JacksonInject(value = QueryJson.TERMINOLOGIES, useInput = OptBoolean.FALSE)
            final List<Terminology> terminologies,
        @JsonProperty(value = "term", required = true) final String incompleteTerm) {}

    /**
     * Written as {@code term}.
     *
     * @return the incomplete term
     */
    @JsonProperty("term")
    abstract String getIncompleteTerm();
  }

  /** Names each value match type. The matches are records, which Jackson reads and writes. */
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = QueryJson.TYPE_PROPERTY)
  @JsonSubTypes({
    @JsonSubTypes.Type(value = Exact.class, name = "exact"),
    @JsonSubTypes.Type(value = Contains.class, name = "contains"),
    @JsonSubTypes.Type(value = StartsWith.class, name = "startsWith"),
    @JsonSubTypes.Type(value = Fuzzy.class, name = "fuzzy"),
    @JsonSubTypes.Type(value = SearchAsYouType.class, name = "searchAsYouType"),
    @JsonSubTypes.Type(value = OneOf.class, name = "oneOf"),
    @JsonSubTypes.Type(value = CaseInsensitive.class, name = "caseInsensitive")
  })
  abstract static class ValueMatchMixin {}

  /** {@link OneOf}: {@code terms}, written sorted so a set always writes the same JSON. */
  abstract static class OneOfMixin {

    /**
     * Written as {@code terms}, sorted.
     *
     * @return the terms
     */
    @JsonProperty("terms")
    @JsonSerialize(converter = SortedTerms.class)
    abstract Set<String> terms();
  }

  /** Sorts a set of terms. */
  static final class SortedTerms extends StdConverter<Set<String>, SortedSet<String>> {

    /* see superclass */
    @Override
    public SortedSet<String> convert(final Set<String> terms) {
      return new TreeSet<>(terms);
    }
  }

  /** Names each qualifier condition type. */
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = QueryJson.TYPE_PROPERTY)
  @JsonSubTypes({
    @JsonSubTypes.Type(value = PropertyQualifier.class, name = "qualifier"),
    @JsonSubTypes.Type(value = AllQualifiers.class, name = "allQualifiers"),
    @JsonSubTypes.Type(value = AnyQualifier.class, name = "anyQualifier"),
    @JsonSubTypes.Type(value = NoQualifier.class, name = "noQualifier")
  })
  abstract static class QualifierConditionMixin {}

  /** {@link PropertyQualifier}: {@code qualifierType} and an optional {@code value}. */
  @JsonDeserialize(builder = JsonBuilders.QualifierBuilder.class)
  abstract static class PropertyQualifierMixin {

    /**
     * Written as {@code qualifierType}.
     *
     * @return the type match
     */
    @JsonProperty("qualifierType")
    abstract ValueMatch getTypeNameOrCodeMatch();

    /**
     * Written as {@code value}, when there is one.
     *
     * @return the value match, or null
     */
    @JsonProperty("value")
    abstract ValueMatch getValueMatch();
  }

  /** {@link AllQualifiers}: {@code conditions}. */
  abstract static class AllQualifiersMixin {

    /**
     * Builds the condition.
     *
     * @param conditions the conditions that must all hold
     */
    @JsonCreator
    AllQualifiersMixin(
        @JsonProperty(value = "conditions", required = true)
            final List<QualifierCondition> conditions) {}
  }

  /** {@link AnyQualifier}: {@code conditions}. */
  abstract static class AnyQualifierMixin {

    /**
     * Builds the condition.
     *
     * @param conditions the alternative conditions
     */
    @JsonCreator
    AnyQualifierMixin(
        @JsonProperty(value = "conditions", required = true)
            final List<QualifierCondition> conditions) {}
  }

  /** {@link NoQualifier}: {@code condition}. */
  abstract static class NoQualifierMixin {

    /**
     * Builds the condition.
     *
     * @param condition the condition the entry must not meet
     */
    @JsonCreator
    NoQualifierMixin(
        @JsonProperty(value = "condition", required = true) final QualifierCondition condition) {}
  }
}
