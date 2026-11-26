package com.safeflow.global.exception

class TransactionNotFoundException : SafeFlowException(ErrorCode.TRANSACTION_NOT_FOUND)

class TransactionNotBlockedException : SafeFlowException(ErrorCode.TRANSACTION_NOT_BLOCKED)

class TransactionAlreadyJudgedException : SafeFlowException(ErrorCode.TRANSACTION_ALREADY_JUDGED)

class InvalidOccurredAtException : SafeFlowException(ErrorCode.INVALID_INPUT)
