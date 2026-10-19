# EVS Query Tree

This package models a concept search as a tree of small query objects. Each node turns itself into
an OpenSearch `QueryBuilder`. Behavior is chosen by which classes you put in the tree (dynamic
dispatch), not by switching on a `type` string as `OpenSearchServiceImpl.getTermQuery` does.

- **Atomic queries** are the leaves. Each one matches a single field using a single strategy.
- **Composite queries** (planned) combine other queries with `and`, `or`, and so on.

Because every node has the same interface, any query can be nested inside any composite:

```lisp
(and (fuzzy  "normName" "melanoma")
     (or (match       "code"     "C3224")
         (starts-with "normName" "blood")))
```

## Hierarchy

```mermaid
classDiagram
    direction TB

    class EvsQuery {
        <<abstract>>
        #List~Terminology~ terminologies
        +getTerminologies() List~Terminology~
        +getQueryBuilder()* QueryBuilder
        +getSorts() List~SortBuilder~
        +getNativeSearchQuery(Pageable, IncludeParam) NativeSearchQuery
        +getIndexCoordinates() IndexCoordinates
    }

    class AtomicQuery {
        <<abstract>>
        #String field
        #String term
        +getField() String
        +getTerm() String
    }

    class SupportsIgnoringCase {
        <<interface>>
        +getCaseInsensitiveQueryBuilder() QueryBuilder
    }

    class CaseInsensitive {
        -SupportsIgnoringCase query
        +getQueryBuilder() QueryBuilder
    }

    class Match {
        +getQueryBuilder() TermQueryBuilder
    }
    class Contains {
        +getQueryBuilder() WildcardQueryBuilder
    }
    class StartsWith {
        +getQueryBuilder() PrefixQueryBuilder
    }
    class Fuzzy {
        -Fuzziness fuzziness
        +getQueryBuilder() FuzzyQueryBuilder
    }

    class Typeahead {
        -String incompleteTerm
        +getHasWords(String)$ boolean
        +getQueryBuilder() QueryBuilder
        +getSorts() List~SortBuilder~
    }

    class CompositeQuery {
        <<planned>>
        #List~EvsQuery~ children
    }

    EvsQuery <|-- AtomicQuery
    EvsQuery <|-- CompositeQuery
    EvsQuery <|-- CaseInsensitive
    EvsQuery <|-- Typeahead
    AtomicQuery <|-- Match
    AtomicQuery <|-- Contains
    AtomicQuery <|-- StartsWith
    AtomicQuery <|-- Fuzzy
    SupportsIgnoringCase <|.. Match
    SupportsIgnoringCase <|.. Contains
    SupportsIgnoringCase <|.. StartsWith
    CompositeQuery o-- EvsQuery : children
    CaseInsensitive o-- SupportsIgnoringCase : wraps
```

| Class | Role |
|---|---|
| `EvsQuery` | Base type for every node. Its contract is `getQueryBuilder()`. It also holds an unmodifiable copy of the terminologies being searched, and (experimental, see [Sorting](#sorting-experimental)) contributes sorts and builds a whole search request. Code that accepts a query should accept `EvsQuery`, so leaves and composites can be used interchangeably. |
| `AtomicQuery` | Base type for leaves. It adds the `field` and `term` every leaf needs and rejects `null` for either. Subclasses only decide which OpenSearch query to build. |
| `SupportsIgnoringCase` | Implemented by queries that can also run ignoring case: `Match`, `StartsWith`, and `Contains`. `Fuzzy` doesn't, because OpenSearch has no case-insensitive fuzzy query. |
| `CaseInsensitive` | A wrapper node. It runs the wrapped query ignoring case by calling `getCaseInsensitiveQueryBuilder()`. The wrapped query is not changed. |
| `Typeahead` | The query behind `/concept/typeahead`. Matches preferred names with `SearchAsYouType` and overrides `getSorts()` to rank general concepts with short names first. `Typeahead.getHasWords(term)` says whether a term has anything to complete; the constructor rejects terms that don't. |
| `CompositeQuery` | Not implemented yet. A composite holds child `EvsQuery` nodes instead of a field and a term. |

## Atomic queries

Apart from `SearchAsYouType`, the atomic queries are **case sensitive** and compare against the
**whole indexed value**. To ignore case, wrap one in `CaseInsensitive` (see below).
`SearchAsYouType` works on individual words and always ignores case.

| Class | Matches when the value… | OpenSearch query |
|---|---|---|
| `Match` | equals the term exactly | `term` |
| `Contains` | includes the term anywhere | `wildcard` on `*term*`, with `*`, `?`, and `\` in the term escaped |
| `StartsWith` | begins with the term | `prefix` |
| `Fuzzy` | is within a small edit distance of the term (`Fuzziness.AUTO` by default, or pass one) | `fuzzy` |
| `SearchAsYouType` | contains every typed word in any order, with only the last word allowed to be partial. Ignores case. Needs a field mapped as `search_as_you_type` | `multi_match` of type `bool_prefix` over the field and its `._2gram` / `._3gram` subfields |

### Choosing a field

Whole-value matching only works on `Keyword` fields. On an analyzed `Text` field such as `name`,
OpenSearch stores lowercased tokens, so these queries match individual words instead of the full
value.

Today **no field stores concept or synonym names in their original case**:

- `name` and `synonyms.name` are `Text`, so they're tokenized and lowercased.
- `normName` and `synonyms.normName` are `Keyword`, but `ConceptUtils.normalize` lowercases them
  and strips punctuation first.

So against current indexes, `Match`, `Contains`, and the others are effectively case insensitive
on `normName`, and the term should be normalized the same way. True case-sensitive name matching
needs a keyword subfield (e.g. `name.keyword` via `@MultiField`, as `Mapping.source` does) and a
reindex.

Fields under `synonyms`, `definitions`, and `properties` are nested. Queries on them must be
wrapped in a `nestedQuery`, which is a natural job for a future `Nested` composite.

### Usage

```java
final EvsQuery query = new StartsWith(terminologies, "normName", "melan");
boolQuery.must(query.getQueryBuilder());
```

## Ignoring case

`CaseInsensitive` wraps a query that implements `SupportsIgnoringCase`:

```java
// Finds "Melanoma", "melanoma", and "Malignant Melanoma"
final EvsQuery query =
    new CaseInsensitive(new Contains(terminologies, "name", "MELANOMA"));
```

The constructor only accepts queries that implement `SupportsIgnoringCase`, so
`new CaseInsensitive(new Fuzzy(...))` doesn't compile. To let a new query be wrapped, implement
`SupportsIgnoringCase` and return the same OpenSearch query with case ignored, usually by calling
`.caseInsensitive(true)` on the builder.

## Sorting (experimental)

> **Experimental** (branch `eas/typeahead-composed-sorts`). This lets query nodes contribute
> sorts that compose by priority. If it turns out not to fit, go back to commit `5765d1eb`, where
> `Typeahead` supplied its sorts and `OpenSearchServiceImpl` built the request. The alternatives
> considered were ranking through the score (`script_score` / `rank_feature`, which blends across
> branches) and a separate top-level sort object (an `EvsSort` tree passed alongside the query).

Any node can be the outermost query of a search request:

```java
final NativeSearchQuery search = query.getNativeSearchQuery(pageable, include);
operations.search(search, Concept.class, query.getIndexCoordinates());
```

The request's sort order comes from `getSorts()`. Most nodes only match and return no sorts.
`Typeahead` returns its ranking sort. The rules:

1. **A node's own sorts come first, then each child's sorts in child order.** Composites must
   implement `getSorts()` this way. A nested node's sorts therefore only break ties left by the
   nodes above and before it, like `ORDER BY a, b, c` in SQL. `And(a, b)` and `And(b, a)` match the
   same concepts but can order them differently.
2. **Negating composites contribute nothing.** A `Not` returns none of its children's sorts.
3. **Duplicates are dropped.** The first occurrence of a sort keeps its position.
4. **`getNativeSearchQuery` always appends the tie-break `_index`, then `code`.** Each terminology
   has its own index, so this orders by terminology, then code, and results are stable between
   requests. (`terminology` is analyzed text and can't be sorted on.)

Known limits:

- **A sort ranks every hit, not just the hits its node matched.** Under `And`, every hit matched
  every child, so this doesn't come up. Under `Or`, a child's sort also orders hits that only its
  sibling matched. Script sorts like `Typeahead`'s work on any concept. Field sorts put documents
  missing the field last.
- **There's no way to replace the tree's sorts yet,** for example with a user-chosen "name A to Z".
  Add an override parameter to `getNativeSearchQuery` when one is needed.
- **Sorts can't blend.** A higher-priority sort always wins outright. Blending relevance across
  branches needs ranking through the score instead.

## Extending

### Adding an atomic query

Extend `AtomicQuery`, pass the constructor arguments through, and build the OpenSearch query from
`field` and `term`. For example, a regular-expression leaf:

```java
package gov.nih.nci.evs.api.query;

import gov.nih.nci.evs.api.model.Terminology;
import java.util.List;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;

/** Matches values against a regular expression (case sensitive). */
public class Regex extends AtomicQuery {

  /**
   * Instantiates a {@link Regex}.
   *
   * @param terminologies the terminologies being searched
   * @param field the field to match against
   * @param term the regular expression the value must match
   */
  public Regex(final List<Terminology> terminologies, final String field, final String term) {
    super(terminologies, field, term);
  }

  /* see superclass */
  @Override
  public QueryBuilder getQueryBuilder() {
    return QueryBuilders.regexpQuery(field, term);
  }
}
```

### Adding a composite query

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
  public QueryBuilder getQueryBuilder() {
    final BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
    for (final EvsQuery child : children) {
      boolQuery.must(child.getQueryBuilder());
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
            new Fuzzy(terminologies, "normName", "melanoma"),
            new Or(
                terminologies,
                List.of(
                    new Match(terminologies, "code", "C3224"),
                    new StartsWith(terminologies, "normName", "blood")))));
```

### Checklist for a new query

1. Name it after what it matches, and document that behavior in the class Javadoc.
2. Build an OpenSearch query in `getQueryBuilder()`. Don't run searches or read services there.
3. Escape any characters in the term that are syntax for the OpenSearch query you use (see
   `Contains.escapeWildcards`).
4. If the OpenSearch query supports ignoring case, implement `SupportsIgnoringCase`.
5. Add a lesson to `AtomicQueryTest` (or a similar test) that runs the query against the test index
   and asserts which values it finds. Test behavior, not the built `QueryBuilder`.
6. Run `./gradlew spotlessApply`.
