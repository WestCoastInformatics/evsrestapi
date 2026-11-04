package gov.nih.nci.evs.api.query;

/**
 * Matches concepts that have a role of the given type, optionally pointing to the given target,
 * e.g. concepts whose primary anatomic site is Skin or anything below Skin. A {@link
 * HasRelationship} on the concept's {@code roles}: see it for how matching works.
 */
public final class HasRole extends HasRelationship {

  /** The concept document's list of roles, mapped as nested so roles can be searched. */
  private static final String ROLES_FIELD_NAME = "roles";

  /**
   * Instantiates a {@link HasRole} that matches a role of the given type, pointing to any concept.
   *
   * @param roleType the role's type
   */
  public HasRole(final RelationshipType roleType) {
    super(roleType);
  }

  /**
   * Instantiates a {@link HasRole} that matches a role of the given type pointing to the target.
   *
   * @param roleType the role's type
   * @param roleTarget the concepts the role must point to
   */
  public HasRole(final RelationshipType roleType, final RelationshipTarget roleTarget) {
    super(roleType, roleTarget);
  }

  /* see superclass */
  @Override
  protected String getRelationshipListFieldName() {
    return ROLES_FIELD_NAME;
  }
}
