package gov.nih.nci.evs.api.query;

import gov.nih.nci.evs.api.model.Terminology;
import java.util.List;

/**
 * Matches concepts that another concept points to with a role of the given type, optionally from
 * the given source, e.g. concepts that are the primary anatomic site of Melanoma or anything below
 * Melanoma. A {@link HasRelationship} on the concept's {@code inverseRoles}: see it for how
 * matching works.
 *
 * <p>An inverse role is a role seen from the concept it points to: its type is the role's type, and
 * its target ({@code relatedCode}, {@code relatedName}) is the concept holding the role. So Skin's
 * inverse roles include "primary site: Melanoma" when Melanoma's roles include "primary site:
 * Skin".
 */
public final class HasInverseRole extends HasRelationship {

  /** The concept document's list of inverse roles, mapped as nested so they can be searched. */
  private static final String INVERSE_ROLES_FIELD_NAME = "inverseRoles";

  /**
   * Instantiates a {@link HasInverseRole} that matches an inverse role of the given type, from any
   * concept.
   *
   * @param terminologies the terminologies being searched
   * @param roleType the role's type
   */
  public HasInverseRole(final List<Terminology> terminologies, final RelationshipType roleType) {
    super(terminologies, roleType);
  }

  /**
   * Instantiates a {@link HasInverseRole} that matches an inverse role of the given type from the
   * source.
   *
   * @param terminologies the terminologies being searched
   * @param roleType the role's type
   * @param roleSource the concepts holding the role that points to the matched concept
   */
  public HasInverseRole(
      final List<Terminology> terminologies,
      final RelationshipType roleType,
      final RelationshipTarget roleSource) {
    super(terminologies, roleType, roleSource);
  }

  /* see superclass */
  @Override
  protected String getRelationshipListFieldName() {
    return INVERSE_ROLES_FIELD_NAME;
  }
}
