package br.com.tiagotds.transfereasy.application.handler;

import br.com.tiagotds.transfereasy.application.command.TransferMoney;
import br.com.tiagotds.transfereasy.domain.error.NotFound;
import br.com.tiagotds.transfereasy.domain.model.Transfer;
import br.com.tiagotds.transfereasy.domain.model.TransferReceipt;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public final class TransferHandler extends MoneyMovementHandler<TransferMoney, TransferReceipt> {

    private final Supplier<UUID> ids;

    public TransferHandler(MovementDependencies deps, Supplier<UUID> ids) {
        super(deps);
        this.ids = ids;
    }

    @Override
    public TransferReceipt handle(TransferMoney command) {
        var amount = validAmount(command.amount());
        var loaded = load(List.of(command.from(), command.to()));
        var from = pick(loaded, command.from(), () -> NotFound.account(command.from()));
        var to = pick(loaded, command.to(), NotFound::destinationAccount);
        var transfer = Transfer.between(from, to, amount, ids.get().toString(), now());
        return new TransferReceipt(transfer.id(), record(transfer.debit()), record(transfer.credit()));
    }
}
