package br.com.tiagotds.transfereasy.api.exception;

import java.io.Serializable;

public class TransfereasyException extends Exception implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -8397635857157497406L;

	public enum ExceptionType {
		NOT_FOUND, ALREADY_EXISTS, NO_BALANCE_ENOUGH, UNDEFINED, INVALID
	}

	private ExceptionType type;

	public TransfereasyException(String message, ExceptionType type) {
		super(message);
		this.type = type;
	}

	public ExceptionType getType() {
		return this.type;
	}

}
