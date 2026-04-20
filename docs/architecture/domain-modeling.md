<!-- Derived from AGENTS.md — keep in sync -->

# Domain Modeling

Shelf follows a strict **"Make illegal states unrepresentable"** philosophy. Once an object enters the domain layer, it is guaranteed valid and consistent.

## Identity Pattern

The `Identity<S, ID>` type wraps entity IDs with state awareness:

- **`Unsaved`** — ID is logically `Nothing`; the compiler prevents accessing it.
- **`Persisted`** — ID is guaranteed present; access via `.id`.

Use `SavedRoot` for persisted data and `NewRoot` for incoming data. This distinction is enforced at the type level.

## Domain Hierarchy

| Term | Meaning | Example |
|------|---------|---------|
| **Root** | Entry point of an aggregate; standalone identity | `BookRoot`, `AuthorRoot` |
| **Record** | Subordinate data describing a root | `MetadataRecord` |
| **Aggregate** | Full tree: root + records + children | `BookAggregate` |
| **Summary** | Flattened, read-optimized view for lists | `BookSummary` |

### Relationships

- **Strong composition:** a root owns its records (lifecycle bound together).
- **Weak association:** a root relates to another root by ID or `SavedRoot` reference only — never by embedding the full aggregate.

## Value Classes

Domain primitives use `@JvmInline value class` with private constructors:

```kotlin
@JvmInline
value class BookTitle private constructor(val value: String) {
    companion object {
        fun create(raw: String): Either<AppError, BookTitle> =
            // validation logic
    }

    companion object {
        fun fromRaw(value: String): BookTitle = BookTitle(value)
    }
}
```

- Factory functions return `Either<AppError, T>` for untrusted input.
- `fromRaw` is reserved for trusted database rehydration.
- Unwrap via `.value` at the last moment in the persistence layer.

## Error Handling

Shelf uses Arrow's `Either`/`Raise` DSL instead of exceptions for business logic:

```kotlin
context(_: RaiseContext)
fun updateBook(command: UpdateBookCommand): BookAggregate {
    val snapshot = repository.load(command.bookId).bind()
    val mutation = decider.decide(snapshot, command).bind()
    return repository.persist(mutation).bind()
}
```

All domain errors are subtypes of `AppError`. The `either { }` block and `.bind()` calls provide clean, linear error flows without try/catch.

## Mutation Orchestration

Complex write paths follow a standard sequence:

```
load snapshot -> domain decider/aggregate apply -> persist -> domain event handling
```

Domain deciders are pure functions: `(snapshot, command) -> Either<AppError, Mutation>`. Services orchestrate I/O around these pure decisions.

### Mutation Types

Shelf uses sealed/domain-specific mutation types rather than flag+list combinations, ensuring invalid mutation combinations are not representable at the type level.

## Trust Boundaries

| Source | Trust Level |
|--------|------------|
| HTTP input | Untrusted — validate via command mapping |
| Upload metadata | Untrusted — validate before domain entry |
| Filesystem metadata | Untrusted — validate during import scan |
| External provider data | Untrusted — validate at adapter boundary |
| Database rehydration | Trusted — use `fromRaw` |
| Internal service calls | Trusted — types already validated |
