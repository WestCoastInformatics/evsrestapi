package gov.nih.nci.evs.api.query;

/**
 * Matches concepts that have a role of the given type, optionally pointing to the given target,
 * e.g. concepts whose primary anatomic site is Skin. A {@link HasRelationship} on the concept's
 * {@code roles}: see it for how matching works.
 */
public final class HasRole extends HasRelationship {

  /** The concept document's list of roles, mapped as nested so roles can be searched. */
  private static final String ROLES_FIELD_NAME = "roles";

  /** Instantiates an empty {@link HasRole}. */
  public HasRole() {
    // n/a
  }

  /**
   * Instantiates a {@link HasRole} that matches a role of the given type, pointing to any concept.
   *
   * @param roleTypeNameOrCodeMatch how the role's type name or code must match
   */
  public HasRole(final ValueMatch roleTypeNameOrCodeMatch) {
    super(roleTypeNameOrCodeMatch);
  }

  /**
   * Instantiates a {@link HasRole} that matches a role of the given type pointing to the target.
   *
   * @param roleTypeNameOrCodeMatch how the role's type name or code must match
   * @param roleTargetNameOrCodeMatch how the name or code of the concept the role must point to
   */
  public HasRole(
      final ValueMatch roleTypeNameOrCodeMatch, final ValueMatch roleTargetNameOrCodeMatch) {
    super(roleTypeNameOrCodeMatch, roleTargetNameOrCodeMatch);
  }

  /* see superclass */
  @Override
  protected String getRelationshipListFieldName() {
    return ROLES_FIELD_NAME;
  }
}
