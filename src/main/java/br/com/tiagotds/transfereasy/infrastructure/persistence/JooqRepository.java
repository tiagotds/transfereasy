package br.com.tiagotds.transfereasy.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Table;
import org.jooq.TableRecord;

/**
 * Shared plumbing for the jOOQ adapters: always works on the current transaction and maps one table's records to
 * one domain type, so subclasses only state their queries and their mapping.
 *
 * @param <R> generated record type of the table
 * @param <D> domain type the table is mapped to
 */
abstract class JooqRepository<R extends TableRecord<R>, D> {

    private final Table<R> table;

    protected JooqRepository(Table<R> table) {
        this.table = table;
    }

    protected abstract D toDomain(R record);

    protected DSLContext db() {
        return TransactionRunner.current();
    }

    protected D insert(Map<Field<?>, Object> values) {
        return toDomain(db().insertInto(table).set(values).returning().fetchSingle());
    }

    protected Optional<D> findOne(Condition condition) {
        return db().selectFrom(table).where(condition).fetchOptional().map(this::toDomain);
    }

    protected List<D> findAll(Condition condition, OrderField<?>... order) {
        return db().selectFrom(table).where(condition).orderBy(order).fetch(this::toDomain);
    }
}
