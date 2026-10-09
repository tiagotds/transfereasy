package br.com.tiagotds.transfereasy.application.pipeline;

/** A command result, plus whether it was replayed from an earlier execution rather than computed now. */
public record Outcome<R>(R value, boolean replayed) {

    public static <R> Outcome<R> fresh(R value) {
        return new Outcome<>(value, false);
    }

    public static <R> Outcome<R> replayed(R value) {
        return new Outcome<>(value, true);
    }
}
