package com.donguri.global.exception

class TransactionNotFoundException : DonguriException(ErrorCode.TRANSACTION_NOT_FOUND)

class TransactionNotBlockedException : DonguriException(ErrorCode.TRANSACTION_NOT_BLOCKED)

class TransactionAlreadyJudgedException : DonguriException(ErrorCode.TRANSACTION_ALREADY_JUDGED)

class InvalidOccurredAtException : DonguriException(ErrorCode.INVALID_INPUT)
