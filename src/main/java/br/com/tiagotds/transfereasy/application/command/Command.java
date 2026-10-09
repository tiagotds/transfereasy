package br.com.tiagotds.transfereasy.application.command;

/**
 * An intention to change state, carrying already-parsed value objects. {@code R} is the result type, so the bus
 * can return it typed. The hierarchy is sealed: the set of things the system can be asked to do is closed.
 */
public sealed interface Command<R> permits CreateCustomer, OpenAccount, AccountMovement, TransferMoney {
}
