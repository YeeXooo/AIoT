package com.aiot.domain.shared;

import java.util.function.Consumer;
import java.util.function.Function;
import org.springframework.stereotype.Service;

public sealed interface Result<T, E> {

    record Ok<T, E>(T value) implements Result<T, E> { }
    record Err<T, E>(E error) implements Result<T, E> { }

    record Unit() { }

    static <T, E> Result<T, E> ok(T value) {
        return new Ok<>(value);
    }

    static <E> Result<Unit, E> ok() {
        return new Ok<>(new Unit());
    }

    static <T, E> Result<T, E> err(E error) {
        return new Err<>(error);
    }

    default boolean isOk() {
        return this instanceof Ok;
    }

    default boolean isErr() {
        return this instanceof Err;
    }

    default T unwrap() {
        if (this instanceof Ok<T, E> ok) {
            return ok.value;
        }
        throw new IllegalStateException("called unwrap on Err: " + ((Err<T, E>) this).error);
    }

    default E unwrapErr() {
        if (this instanceof Err<T, E> err) {
            return err.error;
        }
        throw new IllegalStateException("called unwrapErr on Ok");
    }

    default <U> Result<U, E> map(Function<T, U> fn) {
        if (this instanceof Ok<T, E> ok) {
            return Result.ok(fn.apply(ok.value));
        }
        return Result.err(((Err<T, E>) this).error);
    }

    default void ifOk(Consumer<T> fn) {
        if (this instanceof Ok<T, E> ok) {
            fn.accept(ok.value);
        }
    }

    default void ifErr(Consumer<E> fn) {
        if (this instanceof Err<T, E> err) {
            fn.accept(err.error);
        }
    }
}
