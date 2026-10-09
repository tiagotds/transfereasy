package br.com.tiagotds.transfereasy.domain.model;

public enum EntryKind {
    DEPOSIT(Direction.CREDIT),
    WITHDRAWAL(Direction.DEBIT),
    TRANSFER_OUT(Direction.DEBIT),
    TRANSFER_IN(Direction.CREDIT);

    public enum Direction { CREDIT, DEBIT }

    private final Direction direction;

    EntryKind(Direction direction) {
        this.direction = direction;
    }

    public Direction direction() {
        return direction;
    }
}
