package gov.nih.nci.evs.api.query;

import gov.nih.nci.evs.api.model.Terminology;
import java.util.List;
import org.opensearch.index.query.QueryBuilder;

/**
 * A node in an EVS search query tree. Atomic queries match a single field with a single strategy;
 * composite queries combine other {@link EvsQuery} nodes.
 */
public abstract class EvsQuery {

  /** The terminologies being searched. Unmodifiable. */
  protected final List<Terminology> terminologies;

  /**
   * Instantiates an {@link EvsQuery}.
   *
   * @param terminologies the terminologies being searched
   */
  protected EvsQuery(final List<Terminology> terminologies) {
    this.terminologies = List.copyOf(terminologies);
  }

  /**
   * Returns the terminologies.
   *
   * @return the terminologies
   */
  public List<Terminology> getTerminologies() {
    return terminologies;
  }

  /**
   * Returns the OpenSearch query for this node.
   *
   * @return the query builder
   */
  public abstract QueryBuilder fetchOpenSearchQuery();
}
