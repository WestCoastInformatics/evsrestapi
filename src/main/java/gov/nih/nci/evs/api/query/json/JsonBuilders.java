package gov.nih.nci.evs.api.query.json;

import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.OptBoolean;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import gov.nih.nci.evs.api.model.Terminology;
import gov.nih.nci.evs.api.query.AncestorOf;
import gov.nih.nci.evs.api.query.AncestorOrSelfOf;
import gov.nih.nci.evs.api.query.DescendantOf;
import gov.nih.nci.evs.api.query.DescendantOrSelfOf;
import gov.nih.nci.evs.api.query.EvsQuery;
import gov.nih.nci.evs.api.query.HasAssociation;
import gov.nih.nci.evs.api.query.HasInverseAssociation;
import gov.nih.nci.evs.api.query.HasInverseRole;
import gov.nih.nci.evs.api.query.HasRole;
import gov.nih.nci.evs.api.query.PropertyQualifier;
import gov.nih.nci.evs.api.query.RelationshipTarget;
import gov.nih.nci.evs.api.query.RelationshipType;
import gov.nih.nci.evs.api.query.ValueMatch;
import java.util.List;

/**
 * Builders that Jackson reads some query classes through, named by their mix-ins in {@link
 * QueryMixins}. A class needs one when its JSON doesn't match a single constructor: which
 * constructor to call depends on which properties are present, e.g. a hierarchy node's {@code code}
 * or {@code anchor}. Jackson sets the properties, then calls {@code build}.
 */
final class JsonBuilders {

  /** Only nested builders. */
  private JsonBuilders() {}

  /** Reads a hierarchy node: {@code code} or {@code anchor} (exactly one), and {@code maxLevel}. */
  abstract static class HierarchyBuilder {

    /** The request's terminologies. Injected, so a {@code terminologies} property is unknown. */
    @JacksonInject(value = QueryJson.TERMINOLOGIES, useInput = OptBoolean.FALSE)
    private List<Terminology> terminologies;

    /** The anchor code, or null. */
    private String code;

    /** The anchor query, or null. */
    private EvsQuery anchor;

    /** The maximum distance from the anchors, or null for any distance. */
    private Integer maxLevel;

    /**
     * Sets the anchor code.
     *
     * @param code the anchor code
     * @return this builder
     */
    @JsonProperty("code")
    HierarchyBuilder withCode(final String code) {
      this.code = code;
      return this;
    }

    /**
     * Sets the anchor query.
     *
     * @param anchor the anchor query
     * @return this builder
     */
    @JsonProperty("anchor")
    HierarchyBuilder withAnchor(final EvsQuery anchor) {
      this.anchor = anchor;
      return this;
    }

    /**
     * Sets the maximum distance from the anchors.
     *
     * @param maxLevel the max level
     * @return this builder
     */
    @JsonProperty("maxLevel")
    HierarchyBuilder withMaxLevel(final Integer maxLevel) {
      this.maxLevel = maxLevel;
      return this;
    }

    /**
     * Builds the node.
     *
     * @return the node
     * @throws IllegalArgumentException if both or neither of the code and anchor are set
     */
    public EvsQuery build() {
      final boolean hasOneAnchor = (code == null) != (anchor == null);
      if (!hasOneAnchor) {
        throw new IllegalArgumentException(
            "A hierarchy node needs exactly one of 'code' or 'anchor'");
      }

      final boolean hasCode = code != null;
      final boolean hasMaxLevel = maxLevel != null;
      if (hasCode) {
        return hasMaxLevel
            ? buildFromCode(terminologies, code, maxLevel)
            : buildFromCode(terminologies, code);
      }

      return hasMaxLevel
          ? buildFromQuery(terminologies, anchor, maxLevel)
          : buildFromQuery(terminologies, anchor);
    }

    /**
     * Builds the node from an anchor code, at any distance.
     *
     * @param terminologies the terminologies
     * @param code the anchor code
     * @return the node
     */
    abstract EvsQuery buildFromCode(List<Terminology> terminologies, String code);

    /**
     * Builds the node from an anchor code, within a distance.
     *
     * @param terminologies the terminologies
     * @param code the anchor code
     * @param maxLevel the max level
     * @return the node
     */
    abstract EvsQuery buildFromCode(List<Terminology> terminologies, String code, int maxLevel);

    /**
     * Builds the node from an anchor query, at any distance.
     *
     * @param terminologies the terminologies
     * @param anchor the anchor query
     * @return the node
     */
    abstract EvsQuery buildFromQuery(List<Terminology> terminologies, EvsQuery anchor);

    /**
     * Builds the node from an anchor query, within a distance.
     *
     * @param terminologies the terminologies
     * @param anchor the anchor query
     * @param maxLevel the max level
     * @return the node
     */
    abstract EvsQuery buildFromQuery(
        List<Terminology> terminologies, EvsQuery anchor, int maxLevel);
  }

  /** Reads a {@link DescendantOf}. */
  @JsonPOJOBuilder
  static final class DescendantOfBuilder extends HierarchyBuilder {

    /* see superclass */
    @Override
    EvsQuery buildFromCode(final List<Terminology> terminologies, final String code) {
      return new DescendantOf(terminologies, code);
    }

    /* see superclass */
    @Override
    EvsQuery buildFromCode(
        final List<Terminology> terminologies, final String code, final int maxLevel) {
      return new DescendantOf(terminologies, code, maxLevel);
    }

    /* see superclass */
    @Override
    EvsQuery buildFromQuery(final List<Terminology> terminologies, final EvsQuery anchor) {
      return new DescendantOf(terminologies, anchor);
    }

    /* see superclass */
    @Override
    EvsQuery buildFromQuery(
        final List<Terminology> terminologies, final EvsQuery anchor, final int maxLevel) {
      return new DescendantOf(terminologies, anchor, maxLevel);
    }
  }

  /** Reads a {@link DescendantOrSelfOf}. */
  @JsonPOJOBuilder
  static final class DescendantOrSelfOfBuilder extends HierarchyBuilder {

    /* see superclass */
    @Override
    EvsQuery buildFromCode(final List<Terminology> terminologies, final String code) {
      return new DescendantOrSelfOf(terminologies, code);
    }

    /* see superclass */
    @Override
    EvsQuery buildFromCode(
        final List<Terminology> terminologies, final String code, final int maxLevel) {
      return new DescendantOrSelfOf(terminologies, code, maxLevel);
    }

    /* see superclass */
    @Override
    EvsQuery buildFromQuery(final List<Terminology> terminologies, final EvsQuery anchor) {
      return new DescendantOrSelfOf(terminologies, anchor);
    }

    /* see superclass */
    @Override
    EvsQuery buildFromQuery(
        final List<Terminology> terminologies, final EvsQuery anchor, final int maxLevel) {
      return new DescendantOrSelfOf(terminologies, anchor, maxLevel);
    }
  }

  /** Reads an {@link AncestorOf}. */
  @JsonPOJOBuilder
  static final class AncestorOfBuilder extends HierarchyBuilder {

    /* see superclass */
    @Override
    EvsQuery buildFromCode(final List<Terminology> terminologies, final String code) {
      return new AncestorOf(terminologies, code);
    }

    /* see superclass */
    @Override
    EvsQuery buildFromCode(
        final List<Terminology> terminologies, final String code, final int maxLevel) {
      return new AncestorOf(terminologies, code, maxLevel);
    }

    /* see superclass */
    @Override
    EvsQuery buildFromQuery(final List<Terminology> terminologies, final EvsQuery anchor) {
      return new AncestorOf(terminologies, anchor);
    }

    /* see superclass */
    @Override
    EvsQuery buildFromQuery(
        final List<Terminology> terminologies, final EvsQuery anchor, final int maxLevel) {
      return new AncestorOf(terminologies, anchor, maxLevel);
    }
  }

  /** Reads an {@link AncestorOrSelfOf}. */
  @JsonPOJOBuilder
  static final class AncestorOrSelfOfBuilder extends HierarchyBuilder {

    /* see superclass */
    @Override
    EvsQuery buildFromCode(final List<Terminology> terminologies, final String code) {
      return new AncestorOrSelfOf(terminologies, code);
    }

    /* see superclass */
    @Override
    EvsQuery buildFromCode(
        final List<Terminology> terminologies, final String code, final int maxLevel) {
      return new AncestorOrSelfOf(terminologies, code, maxLevel);
    }

    /* see superclass */
    @Override
    EvsQuery buildFromQuery(final List<Terminology> terminologies, final EvsQuery anchor) {
      return new AncestorOrSelfOf(terminologies, anchor);
    }

    /* see superclass */
    @Override
    EvsQuery buildFromQuery(
        final List<Terminology> terminologies, final EvsQuery anchor, final int maxLevel) {
      return new AncestorOrSelfOf(terminologies, anchor, maxLevel);
    }
  }

  /** Reads a relationship node: {@code relationshipType} and an optional {@code target}. */
  abstract static class RelationshipBuilder {

    /** The request's terminologies. Injected, so a {@code terminologies} property is unknown. */
    @JacksonInject(value = QueryJson.TERMINOLOGIES, useInput = OptBoolean.FALSE)
    private List<Terminology> terminologies;

    /** The relationship type. */
    private RelationshipType relationshipType;

    /** The target, or null for any target. */
    private RelationshipTarget target;

    /**
     * Sets the relationship type.
     *
     * @param relationshipType the relationship type
     * @return this builder
     */
    @JsonProperty("relationshipType")
    RelationshipBuilder withRelationshipType(final RelationshipType relationshipType) {
      this.relationshipType = relationshipType;
      return this;
    }

    /**
     * Sets the target.
     *
     * @param target the target
     * @return this builder
     */
    @JsonProperty("target")
    RelationshipBuilder withTarget(final RelationshipTarget target) {
      this.target = target;
      return this;
    }

    /**
     * Builds the node.
     *
     * @return the node
     */
    public EvsQuery build() {
      final boolean hasTarget = target != null;
      return hasTarget
          ? build(terminologies, relationshipType, target)
          : build(terminologies, relationshipType);
    }

    /**
     * Builds the node pointing to any target.
     *
     * @param terminologies the terminologies
     * @param relationshipType the relationship type
     * @return the node
     */
    abstract EvsQuery build(List<Terminology> terminologies, RelationshipType relationshipType);

    /**
     * Builds the node pointing to the target.
     *
     * @param terminologies the terminologies
     * @param relationshipType the relationship type
     * @param target the target
     * @return the node
     */
    abstract EvsQuery build(
        List<Terminology> terminologies,
        RelationshipType relationshipType,
        RelationshipTarget target);
  }

  /** Reads a {@link HasRole}. */
  @JsonPOJOBuilder
  static final class HasRoleBuilder extends RelationshipBuilder {

    /* see superclass */
    @Override
    EvsQuery build(final List<Terminology> terminologies, final RelationshipType type) {
      return new HasRole(terminologies, type);
    }

    /* see superclass */
    @Override
    EvsQuery build(
        final List<Terminology> terminologies,
        final RelationshipType type,
        final RelationshipTarget target) {
      return new HasRole(terminologies, type, target);
    }
  }

  /** Reads a {@link HasInverseRole}. */
  @JsonPOJOBuilder
  static final class HasInverseRoleBuilder extends RelationshipBuilder {

    /* see superclass */
    @Override
    EvsQuery build(final List<Terminology> terminologies, final RelationshipType type) {
      return new HasInverseRole(terminologies, type);
    }

    /* see superclass */
    @Override
    EvsQuery build(
        final List<Terminology> terminologies,
        final RelationshipType type,
        final RelationshipTarget target) {
      return new HasInverseRole(terminologies, type, target);
    }
  }

  /** Reads a {@link HasAssociation}. */
  @JsonPOJOBuilder
  static final class HasAssociationBuilder extends RelationshipBuilder {

    /* see superclass */
    @Override
    EvsQuery build(final List<Terminology> terminologies, final RelationshipType type) {
      return new HasAssociation(terminologies, type);
    }

    /* see superclass */
    @Override
    EvsQuery build(
        final List<Terminology> terminologies,
        final RelationshipType type,
        final RelationshipTarget target) {
      return new HasAssociation(terminologies, type, target);
    }
  }

  /** Reads a {@link HasInverseAssociation}. */
  @JsonPOJOBuilder
  static final class HasInverseAssociationBuilder extends RelationshipBuilder {

    /* see superclass */
    @Override
    EvsQuery build(final List<Terminology> terminologies, final RelationshipType type) {
      return new HasInverseAssociation(terminologies, type);
    }

    /* see superclass */
    @Override
    EvsQuery build(
        final List<Terminology> terminologies,
        final RelationshipType type,
        final RelationshipTarget target) {
      return new HasInverseAssociation(terminologies, type, target);
    }
  }

  /** Reads a {@link RelationshipTarget}: {@code query} or {@code match}, exactly one. */
  @JsonPOJOBuilder
  static final class RelationshipTargetBuilder {

    /** The target query, or null. */
    private EvsQuery query;

    /** The target match, or null. */
    private ValueMatch match;

    /**
     * Sets the target query.
     *
     * @param query the query
     * @return this builder
     */
    @JsonProperty("query")
    RelationshipTargetBuilder withQuery(final EvsQuery query) {
      this.query = query;
      return this;
    }

    /**
     * Sets the target match.
     *
     * @param match the match
     * @return this builder
     */
    @JsonProperty("match")
    RelationshipTargetBuilder withMatch(final ValueMatch match) {
      this.match = match;
      return this;
    }

    /**
     * Builds the target.
     *
     * @return the target
     * @throws IllegalArgumentException if both or neither of the query and match are set
     */
    public RelationshipTarget build() {
      final boolean hasOneForm = (query == null) != (match == null);
      if (!hasOneForm) {
        throw new IllegalArgumentException(
            "A relationship target needs exactly one of 'query' or 'match'");
      }

      final boolean hasQuery = query != null;
      return hasQuery ? new RelationshipTarget(query) : new RelationshipTarget(match);
    }
  }

  /** Reads a {@link PropertyQualifier}: {@code qualifierType} and an optional {@code value}. */
  @JsonPOJOBuilder
  static final class QualifierBuilder {

    /** The qualifier type match. */
    private ValueMatch qualifierType;

    /** The qualifier value match, or null for any value. */
    private ValueMatch value;

    /**
     * Sets the qualifier type match.
     *
     * @param qualifierType the match
     * @return this builder
     */
    @JsonProperty("qualifierType")
    QualifierBuilder withQualifierType(final ValueMatch qualifierType) {
      this.qualifierType = qualifierType;
      return this;
    }

    /**
     * Sets the qualifier value match.
     *
     * @param value the match
     * @return this builder
     */
    @JsonProperty("value")
    QualifierBuilder withValue(final ValueMatch value) {
      this.value = value;
      return this;
    }

    /**
     * Builds the qualifier.
     *
     * @return the qualifier
     */
    public PropertyQualifier build() {
      final boolean hasValue = value != null;
      return hasValue
          ? new PropertyQualifier(qualifierType, value)
          : new PropertyQualifier(qualifierType);
    }
  }
}
