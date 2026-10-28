# AGENTS.md

## Scope

This package models a concept search as a tree of query nodes (`EvsQuery`). Each node builds its
OpenSearch query in `fetchOpenSearchQuery(operations)`. Read `README.md` in this package before
adding or changing a node: it covers field and value matches, `CaseInsensitive`, queries about other
concepts (`declareDependencies`), and how to add a new node.

## Combining Conditions: Raw OpenSearch For Now

- Queries will eventually be built almost entirely from logical operator nodes (`And`, `Or`, `Not`,
  `Boost`). On the exploratory branch `eas/explore-filters` they're merged in and may be used to
  combine nodes, e.g. in tests. They're still only lightly tested: on other branches, don't add them
  and don't depend on `eas/logical-operators`.
- Until they land, combine conditions inside `fetchOpenSearchQuery` with raw OpenSearch
  (`QueryBuilders.boolQuery()`, `nestedQuery`, `constantScoreQuery`, ...). This code will be
  refactored onto the logical nodes later.
- Build leaf conditions with `FieldMatch` and a value match (`Exact`, `StartsWith`, ...) and call its
  `fetchOpenSearchQuery` instead of writing `termQuery`/`prefixQuery` by hand.
- `FieldMatch` is only for building nodes. Don't make it public. In tests, use it only inside a
  class, method, or field marked `@DemonstratesFieldMatch`; tutorials like `HasRoleTest` must use
  public nodes. `FieldMatchUsageSourceCodeTest` enforces this.
- Keep the raw combining code in small, clearly named helpers, so each maps onto one logical node.

## Facets

- A node exposes the facet for the dimension it constrains by overriding
  `fetchFacetBuilders(operations)`; build the aggregation with `FacetAggregations` so
  `FacetResults` can read it. Logical operators pass up their children's facets.
- `FacetedSearch` runs a base query plus facets (dimension node + the user's picks) the standard way;
  see "Facets" in `README.md`.

## Tests

- `ValueMatchTest`, `HasRoleTest`, and `HasAssociationTest` run against OpenSearch on port 9201.
- `DeclareDependenciesTest` uses a mocked `OpenSearchOperations` and needs no services.
- `FacetedSearchTest` runs against OpenSearch on port 9201; `FacetAggregationsTest` needs no services.
