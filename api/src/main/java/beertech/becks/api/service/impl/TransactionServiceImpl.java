package beertech.becks.api.service.impl;

import static beertech.becks.api.model.TypeOperation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import beertech.becks.api.model.TypeOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import beertech.becks.api.entities.Transaction;
import beertech.becks.api.exception.account.AccountDoesNotExistsException;
import beertech.becks.api.exception.transaction.InvalidTransactionOperationException;
import beertech.becks.api.repositories.AccountRepository;
import beertech.becks.api.repositories.TransactionRepository;
import beertech.becks.api.service.TransactionService;
import beertech.becks.api.tos.request.TransactionRequestTO;
import org.springframework.web.bind.MethodArgumentNotValidException;

@Service
public class TransactionServiceImpl implements TransactionService {

	@Autowired
	private TransactionRepository transactionRepository;

	@Autowired
	private AccountRepository accountRepository;

	@Override
	public void createTransaction(TransactionRequestTO transactionTO)
			throws AccountDoesNotExistsException, InvalidTransactionOperationException {

		// TODO verificar se tem como fazer isso de uma maneira melhor
		transactionTO.setOperation(transactionTO.getOperation().toUpperCase());

		validateTransaction(transactionTO.getOriginAccountCode(), transactionTO.getDestinationAccountCode(),
				transactionTO.getOperation());

		LocalDateTime transactionTime = LocalDateTime.parse(transactionTO.getTransactionTime(),
				DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));

		if (TRANSFERENCIA.getDescription().equals(transactionTO.getOperation())) {
			createTransferTransaction(transactionTO, transactionTime);
		} else {
			createSelfTransaction(transactionTO, transactionTime);
		}
	}

	/**
	 * This method validates the transaction
	 * 
	 * @param originAccountCode      the code of the origin account
	 * @param destinationAccountCode the code of the destination account
	 * @param operation              the operation of this transaction
	 * @throws AccountDoesNotExistsException        when an informed account is not
	 *                                              found on the database
	 * @throws InvalidTransactionOperationException when the informed operation does
	 *                                              not exist
	 */
	private void validateTransaction(String originAccountCode, String destinationAccountCode, String operation)
			throws AccountDoesNotExistsException, InvalidTransactionOperationException {

		if (Arrays.stream(values()).noneMatch(op -> op.getDescription().equals(operation))) {
			throw new InvalidTransactionOperationException(operation);
		}

		if (!accountRepository.existsByCode(originAccountCode)) {
			throw new AccountDoesNotExistsException(originAccountCode);
		}

		if (TRANSFERENCIA.getDescription().equals(operation)) {
			if (destinationAccountCode == null || destinationAccountCode.isEmpty()) {
				// TODO throw MethodArgumentNotValidException ????
			}

			if (!accountRepository.existsByCode(destinationAccountCode)) {
				throw new AccountDoesNotExistsException(destinationAccountCode);
			}
		}
	}

	/**
	 * Creates a transfer transaction
	 *
	 * @param transactionTO   the transaction to
	 * @param transactionTime the time of this transaction
	 */
	private void createTransferTransaction(TransactionRequestTO transactionTO, LocalDateTime transactionTime) {
		List<Transaction> transactionsToSave = new ArrayList<>();

		Transaction debitTransaction = Transaction.builder().typeOperation(TRANSFERENCIA)
				.valueTransaction(transactionTO.getValue().negate()).dateTime(transactionTime).build();
		accountRepository.findByCode(transactionTO.getOriginAccountCode())
				.ifPresent(account -> debitTransaction.setAccountId(account.getId()));
		transactionsToSave.add(debitTransaction);

		Transaction creditTransaction = Transaction.builder().typeOperation(TRANSFERENCIA)
				.valueTransaction(transactionTO.getValue()).dateTime(transactionTime).build();
		accountRepository.findByCode(transactionTO.getDestinationAccountCode())
				.ifPresent(account -> creditTransaction.setAccountId(account.getId()));
		transactionsToSave.add(creditTransaction);

		transactionRepository.saveAll(transactionsToSave);
	}

	/**
	 * Creates a transaction in a single account
	 *
	 * @param transactionTO   the transaction to
	 * @param transactionTime the time of this transaction
	 */
	private void createSelfTransaction(TransactionRequestTO transactionTO, LocalDateTime transactionTime) {
		Transaction transaction = Transaction.builder().dateTime(transactionTime).build();

		accountRepository.findByCode(transactionTO.getOriginAccountCode())
				.ifPresent(account -> transaction.setAccountId(account.getId()));

		if (SAQUE.getDescription().equals(transactionTO.getOperation())) {
			transaction.setValueTransaction(transactionTO.getValue().negate());
			transaction.setTypeOperation(SAQUE);
		} else if (DEPOSITO.getDescription().equals(transactionTO.getOperation())) {
			transaction.setValueTransaction(transactionTO.getValue());
			transaction.setTypeOperation(DEPOSITO);
		}

		transactionRepository.save(transaction);
	}

}