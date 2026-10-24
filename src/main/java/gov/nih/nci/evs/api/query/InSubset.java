package gov.nih.nci.evs.api.query;

import gov.nih.nci.evs.api.model.Terminology;
import gov.nih.nci.evs.api.model.TerminologyMetadata;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.opensearch.data.core.OpenSearchOperations;
import org.opensearch.index.query.QueryBuilder;

/**
 * Matches concepts that are members of the given subsets, e.g. every concept in CDISC SDTM
 * Terminology (C66830). A subset is itself a concept, and membership is an association from the
 * member to the subset: in NCIt, Concept_In_Subset (A8). So this is a {@link HasAssociation} whose
 * type is the terminology's membership association, read from {@link
 * TerminologyMetadata#getSubsetMember}.
 *
 * <p>The subsets are a {@link RelationshipTarget}, so they can be given the same ways as any
 * relationship's target:
 *
 * <ul>
 *   <li>A value match on the subset's code or name, e.g. {@code new OneOf(Set.of(sdtm, send))} for
 *       members of either subset.
 *   <li>A query over the subset concepts, run first, e.g. {@code new DescendantOrSelfOf(t, cdisc)}
 *       for members of any CDISC subset, since NCIt's subsets form a hierarchy.
 * </ul>
 *
 * <p><b>Exploratory.</b> Combinations other than "any of" (in all of several subsets, in one but
 * not another) combine {@link InSubset}s with {@link And} and {@link Not}: each membership is its
 * own association entry, so combining per concept is correct.
 *
 * <p>Unlike the current search's {@code subset} parameter, this doesn't narrow a CDISC grouper
 * subset to members named "CDISC ...". That special case is business logic in {@code
 * OpenSearchServiceImpl}, left out until it's decided whether the query language should keep it.
 */
public final class InSubset extends EvsQuery {

  /**
   * The association types marking membership (e.g. A8) across every searched terminology, so one
   * query covers them all.
   */
  private final Set<String> membershipTypeNamesOrCodes;

  private final RelationshipTarget subsets;

  /**
   * Instantiates an {@link InSubset} that matches members of one subset, given by its exact code.
   *
   * @param terminologies the terminologies being searched
   * @param subsetCode the subset's code
   */
  public InSubset(final List<Terminology> terminologies, final String subsetCode) {
    this(
        terminologies,
        new RelationshipTarget(new Exact(Objects.requireNonNull(subsetCode, "subsetCode"))));
  }

  /**
   * Instantiates an {@link InSubset} that matches members of any of the given subsets.
   *
   * @param terminologies the terminologies being searched
   * @param subsets the subsets, as a match on their code or name or as a query over them
   * @throws IllegalArgumentException if no terminology has a subset membership association
   */
  public InSubset(final List<Terminology> terminologies, final RelationshipTarget subsets) {
    super(terminologies);
    this.subsets = Objects.requireNonNull(subsets, "subsets");
    this.membershipTypeNamesOrCodes = getMembershipTypeNamesOrCodes(terminologies);
  }

  /* see superclass */
  @Override
  public QueryBuilder fetchOpenSearchQuery(final OpenSearchOperations operations) {
    final RelationshipType membershipType =
        new RelationshipType(new OneOf(membershipTypeNamesOrCodes));
    final HasAssociation membership = new HasAssociation(terminologies, membershipType, subsets);

    return membership.fetchOpenSearchQuery(operations);
  }

  /**
   * Returns every searched terminology's subset membership associations. Fails when there are none,
   * since nothing could match.
   *
   * @param terminologies the terminologies being searched
   * @return the membership association names or codes
   * @throws IllegalArgumentException if no terminology has a subset membership association
   */
  private static Set<String> getMembershipTypeNamesOrCodes(final List<Terminology> terminologies) {
    final Set<String> membershipTypeNamesOrCodes = new HashSet<>();
    for (final Terminology terminology : terminologies) {
      final boolean hasMetadata = terminology.getMetadata() != null;
      if (hasMetadata) {
        membershipTypeNamesOrCodes.addAll(terminology.getMetadata().getSubsetMember());
      }
    }

    final boolean hasMembershipType = !membershipTypeNamesOrCodes.isEmpty();
    if (!hasMembershipType) {
      throw new IllegalArgumentException(
          "No searched terminology has a subset membership association (metadata subsetMember)");
    }

    return Set.copyOf(membershipTypeNamesOrCodes);
  }
}
