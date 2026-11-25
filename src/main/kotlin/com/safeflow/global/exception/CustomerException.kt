package com.safeflow.global.exception

class CustomerNotFoundException : SafeFlowException(ErrorCode.CUSTOMER_NOT_FOUND)

class InsufficientBalanceException : SafeFlowException(ErrorCode.INSUFFICIENT_BALANCE)
