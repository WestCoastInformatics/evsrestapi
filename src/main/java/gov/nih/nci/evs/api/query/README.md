# EVS Query Tree

This package models a concept search as a tree of small query objects. Each node turns itself into
an OpenSearch `QueryBuilder`. Behavior is chosen by which classes you put in the tree (dynamic
dispatch), not by switching on a `type` string as `OpenSearchServiceImpl.getTermQuery` does.

There are two layers:

- **Queries** (`EvsQuery`) say which concepts match. `FieldMatch` is the leaf: it matches one
  concept field. Composite queries (planned) combine other queries with `and`, `or`, and so on.
  `FieldMatch` is for building nodes, not part of the API: it's package-private, and tests may
  only use it inside a class, method, or field marked `@DemonstratesFieldMatch` (enforced by
  `FieldMatchUsageSourceCodeTest`). Tests showing how to use the API, like `HasRoleTest`, must
  stick to the public nodes.
- **Value matches** (`ValueMatch`) say how one value is compared (exactly, by prefix, within a
  few edits, ...) without naming a field. A `FieldMatch` applies one to a concept field. A node
  that owns its own fields applies one to those, e.g. a relationship's type to the entry's type
  name and code.

Because every query has the same interface, any query can be nested inside any composite:

```lisp
(and (field "normName" (fuzzy "melanoma"))
     (or (field "code"     (exact "C3224"))
         (field "normName" (starts-with "blood"))))
```

## Logical operators: use raw OpenSearch for now

The plan is to build queries almost entirely from logical operator nodes (`And`, `Or`, `Not`,
`Boost`, and similar), with raw OpenSearch `QueryBuilder`s only inside the value matches. Those
nodes exist on a separate branch but **aren't tested yet, so don't build on them.**

Until they land, combine conditions inside your node's `fetchOpenSearchQuery` with raw OpenSearch
(`QueryBuilders.boolQuery()`, `nestedQuery`, `constantScoreQuery`, ...). Expect that code to be
refactored onto the logical nodes later. To keep that refactor mechanical:

- Build leaf conditions with `FieldMatch` and a value match (`Exact`, `StartsWith`, ...) and call
  its `fetchOpenSearchQuery`, rather than writing `termQuery`/`prefixQuery` by hand.
- Keep the raw combining code (`bool`, `must`, `should`, `must_not`, `constant_score`) in small,
  clearly named helpers, so each one maps onto a single logical node.

## Hierarchy

```mermaid
classDiagram
    direction TB

    class EvsQuery {
        <<abstract>>
        #List~Terminology~ terminologies
        +getTerminologies() List~Terminology~
        +fetchOpenSearchQuery(OpenSearchOperations)* QueryBuilder
    }

    class FieldMatch {
        -String field
        -ValueMatch match
    }

    class HasRelationship {
        <<abstract>>
        -RelationshipType relationshipType
        -RelationshipTarget relationshipTarget
        #getRelationshipListFieldName()* String
    }

    class RelationshipType {
        -ValueMatch match
    }

    class RelationshipTarget {
        +RelationshipTarget(ValueMatch nameOrCodeMatch)
        +RelationshipTarget(EvsQuery query)
    }

    class HasProperty {
        -ValueMatch typeNameOrCodeMatch
        -ValueMatch valueMatch
        -List~PropertyQualifier~ qualifiers
    }

    class PropertyQualifier {
        -ValueMatch typeNameOrCodeMatch
        -ValueMatch valueMatch
    }

    class CompositeQuery {
        <<planned>>
        #List~EvsQuery~ children
    }

    class ValueMatch {
        <<interface>>
        +getQueryBuilder(String field) QueryBuilder
    }

    class SupportsIgnoringCase {
        <<interface>>
        +getCaseInsensitiveQueryBuilder(String field) QueryBuilder
    }

    class CaseInsensitive {
        <<record>>
        SupportsIgnoringCase match
    }
    class Exact {
        <<record>>
        String term
    }
    class Contains {
        <<record>>
        String term
    }
    class StartsWith {
        <<record>>
        String term
    }
    class Fuzzy {
        <<record>>
        String term
        Fuzziness fuzziness
    }
    class OneOf {
        <<record>>
        Set~String~ terms
    }
    class SearchAsYouType {
        <<record>>
        String term
    }

    EvsQuery <|-- FieldMatch
    EvsQuery <|-- HasRelationship
    EvsQuery <|-- HasProperty
    class MatchAnyOf {
        -String field
        -Set~String~ values
        +fetchOpenSearchQuery(OpenSearchOperations) TermsQueryBuilder
    }

    class HierarchyQuery {
        <<abstract>>
        -EvsQuery anchorQuery
        -Integer maxLevel
        -boolean isSelfIncluded
        +fetchOpenSearchQuery(OpenSearchOperations) QueryBuilder
        #fetchRelativesQuery(OpenSearchOperations, Terminology, Set~String~)* QueryBuilder
    }
    class DescendantOf
    class DescendantOrSelfOf
    class AncestorOf
    class AncestorOrSelfOf

    EvsQuery <|-- CompositeQuery
    HasRelationship <|-- HasRole
    HasRelationship <|-- HasAssociation
    EvsQuery <|-- InSubset
    InSubset ..> HasAssociation : builds
    InSubset o-- RelationshipTarget : subsets
    CompositeQuery o-- EvsQuery : children
    FieldMatch o-- ValueMatch : match
    HasRelationship o-- RelationshipType
    HasRelationship o-- RelationshipTarget
    HasProperty o-- ValueMatch : type, value
    HasProperty o-- PropertyQualifier : qualifiers
    PropertyQualifier o-- ValueMatch : type, value
    RelationshipType o-- ValueMatch : match
    RelationshipTarget o-- ValueMatch : or
    RelationshipTarget o-- EvsQuery : or

    ValueMatch <|-- SupportsIgnoringCase
    ValueMatch <|.. CaseInsensitive
    ValueMatch <|.. Fuzzy
    ValueMatch <|.. SearchAsYouType
    ValueMatch <|.. OneOf
    SupportsIgnoringCase <|.. Exact
    SupportsIgnoringCase <|.. Contains
    SupportsIgnoringCase <|.. StartsWith
    CaseInsensitive o-- SupportsIgnoringCase : wraps
    EvsQuery <|-- MatchAnyOf
    EvsQuery <|-- HierarchyQuery
    HierarchyQuery <|-- DescendantOf
    HierarchyQuery <|-- AncestorOf
    DescendantOf <|-- DescendantOrSelfOf
    AncestorOf <|-- AncestorOrSelfOf
    HierarchyQuery o-- EvsQuery : anchorQuery
```

| Class | Role |
|---|---|
| `EvsQuery` | Base type for every query node. Its contract is `fetchOpenSearchQuery(operations)`. It also holds an unmodifiable copy of the terminologies being searched. Code that accepts a query should accept `EvsQuery`, so leaves and composites can be used interchangeably. |
| `FieldMatch` | The leaf query. It pairs a concept field with a `ValueMatch` and rejects `null` for either. It only says which field; the match says how. |
| `ValueMatch` | How one value is compared, with no field. Its contract is `getQueryBuilder(field)`. The implementations are records: plain values with no behavior beyond building their query. |
| `SupportsIgnoringCase` | A `ValueMatch` that can also ignore case: `Exact`, `StartsWith`, and `Contains`. `Fuzzy` doesn't, because OpenSearch has no case-insensitive fuzzy query. |
| `CaseInsensitive` | A wrapper match. It runs the wrapped match ignoring case by calling `getCaseInsensitiveQueryBuilder(field)`. The wrapped match is not changed. |
| `HasRelationship` | Matches concepts by a relationship's type and target (see [Queries about other concepts](#queries-about-other-concepts-joins)). |
| `InSubset` | Matches members of subsets. A subset is a concept, and membership is an association to it (NCIt's Concept_In_Subset, A8, read from the terminology's `subsetMember` metadata), so it builds a `HasAssociation`. The subsets are a `RelationshipTarget`: a code, `OneOf` codes for any of several, or a query such as `DescendantOrSelfOf` a subset root. All-of and but-not combine `InSubset`s per concept. Exploratory: drops the current search's CDISC grouper special case. |
| `HasProperty` | Matches concepts with a property of a given type name or code, optionally with a matching value, both on the same property entry. Searches only `properties`, not synonyms or definitions. |
| `PropertyQualifier` | A qualifier a `HasProperty` property must have: a type name or code, optionally with a matching value, on one qualifier entry of that same property. `HasProperty` takes a list of them as an implicit AND. That's exploratory, not a settled design: see `HasProperty`'s Javadoc. Built into the property's nested query, not run as a separate search, because a separate search can only say which concepts have the qualifier, not which property it's on. Remodeled qualifiers (a synonym's source, a definition's source, map fields) aren't property qualifiers. |
| `CompositeQuery` | Not implemented yet. A composite holds child `EvsQuery` nodes instead of a field and a match. |
| `MatchAnyOf` | A leaf matching a field against a set of values with one `terms` query: the many-value form of `FieldMatch` with `Exact`. Overlaps `OneOf`; kept as is from the hierarchy branch. Rejects more than 65,536 values, OpenSearch's default limit. |
| `HierarchyQuery` | Base type for hierarchy nodes. Runs the inner (anchor) query first through `declareDependencies`, then, for each terminology version, matches the anchors' relatives only in that terminology's index. Handles `maxLevel` (shortest distance, at least 1) and `isSelfIncluded`. Subclasses implement `fetchRelativesQuery`. |
| `DescendantOf` | Matches concepts strictly below the anchors, optionally within `maxLevel` levels. Any node can supply the anchors, including another hierarchy node. Terminologies with `ancestors`/`ancestorDepths` match with one query on those fields; older indexes fall back to the stored hierarchy (`StoredHierarchyFallback`, to be removed after reindexing). Convenience constructors take a code. |
| `DescendantOrSelfOf` | `DescendantOf` that also matches the anchors themselves (distance 0). |
| `AncestorOf` | The upward counterpart: matches concepts strictly above the anchors, optionally within `maxLevel` levels. Reads the anchors' stored paths to root (`StoredPaths`), which every index has, so there's no fallback and nothing to remove after reindexing. Nests with `DescendantOf`, e.g. siblings-or-self is `new DescendantOf(t, new AncestorOf(t, x, 1), 1)`. |
| `AncestorOrSelfOf` | `AncestorOf` that also matches the anchors themselves. |
| `RawBoolQueries` | The raw `bool` combining helpers (`getAllOfQuery`, `getAnyOfQuery`, `getMatchNothingQuery`) used until the logical operator nodes land; each maps onto `And`, `Or`, or `Not`. |

## Value matches

Apart from `SearchAsYouType`, the value matches are **case sensitive** and compare against the
**whole indexed value**. To ignore case, wrap one in `CaseInsensitive` (see below).
`SearchAsYouType` works on individual words and always ignores case.

| Record | Matches when the value… | OpenSearch query |
|---|---|---|
| `Exact` | equals the term exactly | `term` |
| `Contains` | includes the term anywhere | `wildcard` on `*term*`, with `*`, `?`, and `\` in the term escaped |
| `StartsWith` | begins with the term | `prefix` |
| `Fuzzy` | is within a small edit distance of the term (`Fuzziness.AUTO` by default, or pass one) | `fuzzy` |
| `OneOf` | equals any one of a set of terms exactly, e.g. the codes a relationship target query found. At most 65,536 terms by default | `terms` |
| `SearchAsYouType` | contains every typed word in any order, with only the last word allowed to be partial. Ignores case. Needs a field mapped as `search_as_you_type` | `multi_match` of type `bool_prefix` over the field and its `._2gram` / `._3gram` subfields |

### Choosing a field

Whole-value matching only works on `Keyword` fields. On an analyzed `Text` field such as `name`,
OpenSearch stores lowercased tokens, so these matches compare individual words instead of the full
value.

Today **no field stores concept or synonym names in their original case**:

- `name` and `synonyms.name` are `Text`, so they're tokenized and lowercased.
- `normName` and `synonyms.normName` are `Keyword`, but `ConceptUtils.normalize` lowercases them
  and strips punctuation first.

So against current indexes, `Exact`, `Contains`, and the others are effectively case insensitive
on `normName`, and the term should be normalized the same way. True case-sensitive name matching
needs a keyword subfield (e.g. `name.keyword` via `@MultiField`, as `Mapping.source` does) and a
reindex.

Fields under `synonyms`, `definitions`, and `properties` are nested. Queries on them must be
wrapped in a `nestedQuery`, which is a natural job for a future `In` composite. Like
`HasRelationship`, it would own the list's field paths and take a `ValueMatch` for each part of
an entry.

### Usage

```java
final EvsQuery query = new FieldMatch(terminologies, "normName", new StartsWith("melan"));
boolQuery.must(query.fetchOpenSearchQuery(operations));
```

## Ignoring case

`CaseInsensitive` wraps a value match that implements `SupportsIgnoringCase`:

```java
// Finds "Melanoma", "melanoma", and "Malignant Melanoma"
final EvsQuery query =
    new FieldMatch(terminologies, "name", new CaseInsensitive(new Contains("MELANOMA")));
```

Case is a property of how a value is compared, so it goes on the match, not on a query. That
puts it on exactly one comparison: a relationship's type can ignore case while its target doesn't.

The constructor only accepts matches that implement `SupportsIgnoringCase`, so
`new CaseInsensitive(new Fuzzy(...))` doesn't compile. To let a new match be wrapped, implement
`SupportsIgnoringCase` and return the same OpenSearch query with case ignored, usually by calling
`.caseInsensitive(true)` on the builder.

## Queries about other concepts (joins)

Some nodes hold a query that describes a *different* concept from the one being matched:

- `HasRelationship`'s target, e.g. `HasRole`'s: "primary site is Skin or anything below Skin" (spec 1.4),
  or `HasAssociation`'s: "in a subset of the CTRP Terminology" (spec 1.5)
- Planned: `Reachable`'s seeds and hops (1.11)

**Roles and associations share one node.** A concept's `roles` and `associations` are both lists of
entries pairing a type (name and code) with a related concept's code and name, so `HasRelationship` searches
either. It's abstract: each subclass says which list it searches by overriding
`getRelationshipListFieldName()`; `HasRole` returns `roles` and `HasAssociation` returns `associations`. `HasRelationship` owns the list: the
nested query on it and the terminology version filter. `RelationshipType` and `RelationshipTarget`
own which entry fields they compare (`type` and `code`; `relatedName` and `relatedCode`) and build
their `FieldMatch`es on them, with the list's name in front (`roles.type`, ...). `roles`
and `associations` are mapped as nested; searching `inverseRoles` or `inverseAssociations` first
needs that mapping and a reindex.

**The type is a value match; the target is a value match or a query.** A `RelationshipType` takes a `ValueMatch`
and applies it to both the entry's type name and its type code, so the caller picks how the type
is matched without naming the entry's fields: `new RelationshipType(new StartsWith("Disease_Has"))`.
There's no default: an exact type is `new RelationshipType(new Exact("R101"))`. A
`RelationshipTarget` takes either a `ValueMatch`, compared directly against the entry's target name
or target code the same way (the name is analyzed text, so the match compares its words), or a
query over concepts, which runs first and whose codes are then matched exactly.

OpenSearch can't join across documents. A nested query only reaches inside the same concept
document, and a role entry only stores its target's code. So
`HasRole(type, Or(FieldMatch(code, Exact(Skin)), DescendantOf(Skin)))` can't become one OpenSearch
query.

**How it works:** building such a node runs the inner query first. `fetchOpenSearchQuery(operations)`
takes the OpenSearch operations so it can. The node calls `declareDependencies(operations,
dependencies...)`, which runs each dependency and pages through every matching concept with
`search_after` (sorted by code, then index, so pages don't overlap). It reads no stored documents,
which can be very large: each hit's code is its first sort value (read from doc values) and its
terminology version is that of the index it came from. It returns one `ConceptKeys` per dependency:
the codes found in each terminology version. The node then matches what it found, e.g. with a
`FieldMatch` with `OneOf` the codes. The paging is done by `EvsQuery.fetchConceptKeys`, which runs the node's own
`fetchOpenSearchQuery`, so a node is written once and works both as a search and as a dependency.
A `RelationshipTarget` given as a query calls `declareDependencies(operations, targetQuery)`, and for
each terminology version it matches concepts from that terminology version with an entry whose
`relatedCode` is `OneOf` its codes. An inner query can hold its own query about another concept, which runs first in the same
way, to any depth. Nodes that don't hold a query about another concept ignore `operations`.

**Why codes are matched per terminology version:** a code alone doesn't identify a concept. Two
versions of a terminology share codes, and a relationship's target code refers to a concept in the
relationship's own terminology version. So a target found in one version must only match
relationships on concepts from that version. `HasRelationship` filters on the exact `terminology.keyword` and `version.keyword` subfields
(the main `terminology` and `version` fields are analyzed text). The filter sits outside the
`nested` query on the list, because a nested query only sees the entry's own fields.

**Why `operations` is a parameter:** a static lookup hides which nodes search and makes tests
change global state; injecting it at construction means every tree containing a search needs it
just to be built. Taking it in `fetchOpenSearchQuery` keeps trees plain data, and lets unit tests pass a
mock (see `DeclareDependenciesTest`).

**Why the inner query always runs first:** handing codes between searches is cheap. On local NCIt
25.12e, OpenSearch ran a `terms` query over about 23,000 codes in 80ms. A plan that starts from the
other side of the join could sometimes pass fewer codes, but it isn't worth a planner. The
benchmark, and the SPARQL (Fuseki) comparison, is written up under 1.4 Role in the project notes.

**Limit:** one `terms` query accepts at most 65,536 codes (OpenSearch's default
`index.max_terms_count`). A relationship target matching more concepts in one terminology version fails with
an `IllegalStateException`, instead of cutting the codes short and silently returning wrong results.

**Reindex:** `terminology.keyword` and `version.keyword` are new subfields, and `roles` and
`associations` are now nested. Existing concept indexes have none of these, so `HasRole`,
`HasAssociation` (and any `HasRelationship`) need a reindex. Each nested entry is its own hidden
document, so NCIt's many associations (e.g. `Concept_In_Subset`) grow the index.

## Extending

### Adding a value match

Add a record implementing `ValueMatch` (or `SupportsIgnoringCase`, if it can ignore case), and
build the OpenSearch query from the field it's given and its own components. For example, a
regular-expression match:

```java
package gov.nih.nci.evs.api.query;

import java.util.Objects;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;

/**
 * Matches values against a regular expression (case sensitive).
 *
 * @param term the regular expression the value must match
 */
public record Regex(String term) implements ValueMatch {

  /**
   * Instantiates a {@link Regex}.
   *
   * @param term the regular expression the value must match
   */
  public Regex {
    Objects.requireNonNull(term, "term");
  }

  /* see superclass */
  @Override
  public QueryBuilder getQueryBuilder(final String field) {
    return QueryBuilders.regexpQuery(field, term);
  }
}
```

It then works everywhere a value match does: `new FieldMatch(terminologies, "code", new
Regex("C12[0-9]+"))`, or a relationship's type.

### Adding a composite query

> Don't add `And`, `Or`, `Not`, or similar yourself: they're coming from a separate branch (see
> [Logical operators](#logical-operators-use-raw-opensearch-for-now)). This section describes how
> composites are meant to work.

Extend `EvsQuery` directly. A composite holds child nodes and combines their builders. It never
needs to know which concrete classes its children are. For example, `And`:

```java
package gov.nih.nci.evs.api.query;

import gov.nih.nci.evs.api.model.Terminology;
import java.util.List;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;

/** Matches when every child query matches. */
public class And extends EvsQuery {

  /** The child queries. */
  private final List<EvsQuery> children;

  /**
   * Instantiates an {@link And}.
   *
   * @param terminologies the terminologies being searched
   * @param children the queries that must all match
   */
  public And(final List<Terminology> terminologies, final List<EvsQuery> children) {
    super(terminologies);
    this.children = List.copyOf(children);
  }

  /* see superclass */
  @Override
  public QueryBuilder fetchOpenSearchQuery(final OpenSearchOperations operations) {
    final BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
    for (final EvsQuery child : children) {
      boolQuery.must(child.fetchOpenSearchQuery(operations));
    }
    return boolQuery;
  }
}
```

`Or` is the same with `should(...)` and `minimumShouldMatch(1)`. `Not` uses `mustNot(...)`.

Composites nest without any extra code:

```java
final EvsQuery query =
    new And(
        terminologies,
        List.of(
            new FieldMatch(terminologies, "normName", new Fuzzy("melanoma")),
            new Or(
                terminologies,
                List.of(
                    new FieldMatch(terminologies, "code", new Exact("C3224")),
                    new FieldMatch(terminologies, "normName", new StartsWith("blood"))))));
```

### Checklist for a new query

1. Name it after what it matches, and document that behavior in the class Javadoc.
2. Build an OpenSearch query in `fetchOpenSearchQuery(operations)` (a query) or
   `getQueryBuilder(field)` (a value match). Only run a search there for a query
   about another concept, through `declareDependencies` (see
   [Queries about other concepts](#queries-about-other-concepts-joins)).
3. Escape any characters in the term that are syntax for the OpenSearch query you use (see
   `Contains.escapeWildcards`).
4. If a value match's OpenSearch query supports ignoring case, implement `SupportsIgnoringCase`.
5. Add a lesson to `ValueMatchTest` (or a similar test) that runs the query against the test index
   and asserts which values it finds. Test behavior, not the built `QueryBuilder`.
6. Run `./gradlew spotlessApply`.
