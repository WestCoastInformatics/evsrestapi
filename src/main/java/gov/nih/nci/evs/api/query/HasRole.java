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
   * @param role how the role's type name or code must match
   */
  public HasRole(final ValueMatch role) {
    super(role);
  }

  /**
   * Instantiates a {@link HasRole} that matches a role of the given type pointing to the target.
   *
   * @param role how the role's type name or code must match
   * @param target how the name or code of the concept the role must point to
   */
  public HasRole(final ValueMatch role, final ValueMatch target) {
    super(role, target);
  }

  /**
   * Returns how the name or code of the role's type, e.g. Disease_Has_Primary_Anatomic_Site (R101)
   * must match.
   *
   * @return the role name or code match
   */
  public ValueMatch getRole() {
    return getRelationshipType();
  }

  /**
   * Sets how the name or code of the role's type, e.g. Disease_Has_Primary_Anatomic_Site (R101)
   * must match.
   *
   * @param role the role name or code match
   */
  public void setRole(final ValueMatch role) {
    setRelationshipType(role);
  }

  /* see superclass */
  @Override
  protected String getRelationshipListFieldName() {
    return ROLES_FIELD_NAME;
  }
}
